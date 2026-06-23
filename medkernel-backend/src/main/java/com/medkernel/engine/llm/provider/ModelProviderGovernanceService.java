package com.medkernel.engine.llm.provider;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.llm.eval.ModelEvalService;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.config.HighRiskChangeGuard;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 模型 provider 治理服务（LLM-08 T13）。
 *
 * <p>由医疗引擎运营员（{@code llm.provider.manage}）配置 provider 接入；运行时解析在 {@link ModelProviderRegistry}。
 * 配置写入与高危启停相互分离：本服务的配置入口始终保存为停用，避免编辑连接参数时意外上线。
 * 前台凭据使用独立用途密钥加密入库，模型调用只读取租户凭据库；配置与凭据分别使用
 * 关系库乐观锁，防止配置、轮换、探活与启停相互覆盖。
 */
@Service
public class ModelProviderGovernanceService {

    private static final Set<String> HTTP_SCHEMES = Set.of("http", "https");
    private static final String HIGH_RISK_RESOURCE_TYPE = "model_provider";
    private static final String CREDENTIAL_HIGH_RISK_RESOURCE_TYPE =
        "model_provider_credential";

    private final ModelProviderConfigRepository repository;
    private final DeploymentFormService deploymentForm;
    private final ModelEvalService evalService;
    private final ModelProviderRegistry registry;
    private final ModelProviderCredentialRepository credentialRepository;
    private final ProviderCredentialCodec credentialCodec;
    private final AuditRecorder auditRecorder;
    private final HighRiskChangeGuard highRiskGuard;

    public ModelProviderGovernanceService(ModelProviderConfigRepository repository,
                                          DeploymentFormService deploymentForm,
                                          ModelEvalService evalService,
                                          ModelProviderRegistry registry,
                                          ModelProviderCredentialRepository credentialRepository,
                                          ProviderCredentialCodec credentialCodec,
                                          AuditRecorder auditRecorder,
                                          HighRiskChangeGuard highRiskGuard) {
        this.repository = repository;
        this.deploymentForm = deploymentForm;
        this.evalService = evalService;
        this.registry = registry;
        this.credentialRepository = credentialRepository;
        this.credentialCodec = credentialCodec;
        this.auditRecorder = auditRecorder;
        this.highRiskGuard = highRiskGuard;
    }

    @Transactional
    public ModelProviderConfig upsertProvider(String providerCode, ModelProviderUpsertRequest request) {
        String tenantId = requireCurrentTenant();
        String code = requireText(providerCode, "provider 编码");
        ModelProviderConfig current = repository.findByTenantIdAndProviderCode(tenantId, code)
            .orElse(null);
        assertExpectedVersion(current, request.expectedVersion());

        ProviderType type = parseType(request.providerType());
        String endpointUri = normalizeEndpoint(type, request.endpointUri());
        String modelVersion = requireText(request.modelVersion(), "模型版本");
        boolean changed = current == null || connectionMaterialChanged(
            current, type, endpointUri, modelVersion);

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelProviderConfig saved = saveWithConflictTranslation(new ModelProviderConfig(
            current == null ? null : current.id(),
            tenantId,
            code,
            type.name(),
            endpointUri,
            modelVersion,
            "N",
            changed ? "NOT_CONNECTED" : current.status(),
            current == null ? now : current.createdAt(),
            current == null ? actor : current.createdBy(),
            now,
            actor,
            current == null ? null : current.version()));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_provider", code, "保存模型 provider " + code);
        return saved;
    }

    /**
     * 返回当前租户指定 provider 的脱敏治理快照。
     */
    @Transactional(readOnly = true)
    public ModelProviderGovernanceView getProvider(String providerCode) {
        String tenantId = requireCurrentTenant();
        String code = requireText(providerCode, "provider 编码");
        ModelProviderConfig config = repository.findByTenantIdAndProviderCode(tenantId, code)
            .orElseThrow(() -> ApiException.notFound("模型 provider " + code));
        return toView(config);
    }

    /**
     * 返回当前租户 Provider 的服务端分页脱敏列表。
     */
    @Transactional(readOnly = true)
    public PageResponse<ModelProviderGovernanceView> listProviders(PageRequest pageRequest) {
        String tenantId = requireCurrentTenant();
        PageRequest safePage = pageRequest == null ? PageRequest.defaults() : pageRequest;
        long total = repository.countByTenantId(tenantId);
        var items = repository.pageByTenantId(
                tenantId,
                safePage.offset(),
                safePage.safeSize())
            .stream()
            .map(this::toView)
            .toList();
        return PageResponse.of(items, safePage, total);
    }

    /**
     * 登记或轮换租户 Provider 凭据，并使连接状态重新进入待验证。
     */
    @Transactional
    public ModelProviderGovernanceView saveCredential(
            String providerCode,
            ModelProviderCredentialUpsertRequest request) {
        String reason = assertCredentialChangeConfirmed(
            request == null ? null : request.reason(),
            request != null && request.confirmedHighRisk());
        String tenantId = requireCurrentTenant();
        String code = requireText(providerCode, "provider 编码");
        highRiskGuard.assertHighRiskAllowed(CREDENTIAL_HIGH_RISK_RESOURCE_TYPE, code);
        ModelProviderConfig current = requireProvider(tenantId, code);
        ModelProviderCredential existing = credentialRepository
            .findByTenantIdAndProviderCode(tenantId, code)
            .orElse(null);
        assertExpectedCredentialVersion(existing, request.expectedVersion());
        ProviderCredentialCodec.EncodedCredential encoded =
            credentialCodec.encode(request.credential());

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelProviderCredential savedCredential = saveCredentialWithConflictTranslation(
            new ModelProviderCredential(
                existing == null ? null : existing.id(),
                tenantId,
                code,
                encoded.ciphertext(),
                encoded.fingerprint(),
                encoded.last4(),
                existing == null ? now : existing.createdAt(),
                existing == null ? actor : existing.createdBy(),
                now,
                actor,
                RequestContext.currentTraceId(),
                existing == null ? null : existing.version()));
        ModelProviderConfig disconnected = saveWithConflictTranslation(
            disconnectedProvider(current, now, actor));
        auditRecorder.record(
            AuditAction.UPDATE,
            "mk_llm_provider_credential",
            code,
            "保存模型 provider 凭据 " + code
                + "（尾标=" + savedCredential.credentialLast4()
                + "，版本=" + savedCredential.version() + "）：" + reason);
        return ModelProviderGovernanceView.from(disconnected, savedCredential);
    }

    /**
     * 移除租户 Provider 凭据并强制断开连接。
     */
    @Transactional
    public ModelProviderGovernanceView removeCredential(
            String providerCode,
            ModelProviderCredentialRemovalRequest request) {
        String reason = assertCredentialChangeConfirmed(
            request == null ? null : request.reason(),
            request != null && request.confirmedHighRisk());
        String tenantId = requireCurrentTenant();
        String code = requireText(providerCode, "provider 编码");
        highRiskGuard.assertHighRiskAllowed(CREDENTIAL_HIGH_RISK_RESOURCE_TYPE, code);
        ModelProviderConfig current = requireProvider(tenantId, code);
        ModelProviderCredential existing = credentialRepository
            .findByTenantIdAndProviderCode(tenantId, code)
            .orElseThrow(() -> ApiException.notFound("模型 provider 凭据 " + code));
        assertExpectedCredentialVersion(existing, request.expectedVersion());

        try {
            credentialRepository.delete(existing);
        } catch (OptimisticLockingFailureException conflict) {
            throw new ApiException(
                ErrorCode.CONFLICT, "provider 凭据版本已变化，请刷新后重试", conflict);
        }
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelProviderConfig disconnected = saveWithConflictTranslation(
            disconnectedProvider(current, now, actor));
        auditRecorder.record(
            AuditAction.DELETE,
            "mk_llm_provider_credential",
            code,
            "移除模型 provider 凭据 " + code
                + "（原尾标=" + existing.credentialLast4()
                + "，版本=" + existing.version() + "）：" + reason);
        return ModelProviderGovernanceView.from(disconnected, null);
    }

    /**
     * 经高危门禁启用指定 provider。
     */
    @Transactional
    public ModelProviderGovernanceView enableProvider(
            String providerCode,
            ModelProviderActivationRequest request) {
        return changeEnabled(providerCode, request, true);
    }

    /**
     * 经高危门禁停用指定 provider。
     */
    @Transactional
    public ModelProviderGovernanceView disableProvider(
            String providerCode,
            ModelProviderActivationRequest request) {
        return changeEnabled(providerCode, request, false);
    }

    /**
     * 对已登记 provider 执行真实连通性探测并持久化健康状态。
     *
     * <p>探活不改变启停状态，也不绕过医学回归评测门禁；它只把适配器实时结果写回唯一状态源，
     * 供 readiness 和运维界面一致读取。
     */
    @Transactional
    public ModelProviderConfig checkHealth(String providerCode) {
        String tenantId = requireCurrentTenant();
        String code = providerCode == null ? "" : providerCode.trim();
        ModelProviderConfig current = repository.findByTenantIdAndProviderCode(tenantId, code)
            .orElseThrow(() -> ApiException.notFound("模型 provider " + code));
        ModelProviderRegistry.ResolvedProvider resolved = registry.resolveByCode(tenantId, code)
            .orElseThrow(() -> new ApiException(
                ErrorCode.BAD_REQUEST, "模型 provider 类型未注册: " + current.providerType()));
        ProviderHealth health = resolved.adapter().checkHealth(current);
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelProviderConfig checked = saveWithConflictTranslation(new ModelProviderConfig(
            current.id(),
            current.tenantId(),
            current.providerCode(),
            current.providerType(),
            current.endpointUri(),
            current.modelVersion(),
            current.enabledFlag(),
            health.name(),
            current.createdAt(),
            current.createdBy(),
            now,
            actor,
            current.version()));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_provider", code,
            "探测模型 provider " + code + " status=" + health.name());
        return checked;
    }

    private ModelProviderGovernanceView changeEnabled(
            String providerCode,
            ModelProviderActivationRequest request,
            boolean enabled) {
        String reason = assertActivationConfirmed(request);
        String capabilityCode = enabled ? requireActivationCapability(request) : null;
        String tenantId = requireCurrentTenant();
        String code = requireText(providerCode, "provider 编码");
        highRiskGuard.assertHighRiskAllowed(HIGH_RISK_RESOURCE_TYPE, code);
        ModelProviderConfig current = repository.findByTenantIdAndProviderCode(tenantId, code)
            .orElseThrow(() -> ApiException.notFound("模型 provider " + code));
        assertExpectedVersion(current, request.expectedVersion());

        if (current.enabled() == enabled) {
            return toView(current);
        }
        if (enabled) {
            assertProviderCanBeEnabled(tenantId, code, current, capabilityCode);
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelProviderConfig saved = saveWithConflictTranslation(new ModelProviderConfig(
            current.id(),
            current.tenantId(),
            current.providerCode(),
            current.providerType(),
            current.endpointUri(),
            current.modelVersion(),
            enabled ? "Y" : "N",
            current.status(),
            current.createdAt(),
            current.createdBy(),
            now,
            actor,
            current.version()));
        auditRecorder.record(
            AuditAction.UPDATE,
            "mk_llm_provider",
            code,
            (enabled ? "启用" : "停用") + "模型 provider " + code
                + (enabled ? "（capability=" + capabilityCode + "）" : "")
                + "：" + reason);
        return toView(saved);
    }

    private String assertActivationConfirmed(ModelProviderActivationRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirmedHighRisk())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "必须明确确认模型 provider 启停的高危影响");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "启停原因不能为空");
        }
        String reason = request.reason().trim();
        if (reason.length() < 8) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "启停原因至少需要 8 个字符，以便形成可核查审计证据");
        }
        if (reason.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "启停原因不能超过 500 字");
        }
        return reason;
    }

    private String requireActivationCapability(ModelProviderActivationRequest request) {
        if (request.capabilityCode() == null || request.capabilityCode().isBlank()) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "启用模型 provider 必须指定已通过医学回归评测的 capabilityCode");
        }
        return request.capabilityCode().trim().toLowerCase(Locale.ROOT);
    }

    private void assertProviderCanBeEnabled(
            String tenantId,
            String code,
            ModelProviderConfig current,
            String capabilityCode) {
        if (!ProviderHealth.HEALTHY.name().equals(current.status())) {
            throw ApiException.conflict("provider 未通过当前真实健康检查，禁止启用");
        }
        ProviderType type = parseType(current.providerType());
        if (type.external() && !deploymentForm.allowsExternalProvider()) {
            throw new ApiException(ErrorCode.ENG_LLM_009);
        }
        if (!evalService.isClearedForGoLive(
                tenantId,
                code,
                current.modelVersion(),
                capabilityCode)) {
            throw new ApiException(ErrorCode.ENG_LLM_008);
        }
    }

    private boolean connectionMaterialChanged(ModelProviderConfig current,
                                              ProviderType type,
                                              String endpointUri,
                                              String modelVersion) {
        return !type.name().equals(current.providerType())
            || !endpointUri.equals(current.endpointUri())
            || !modelVersion.equals(current.modelVersion());
    }

    private ProviderType parseType(String raw) {
        try {
            return ProviderType.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "不支持的 provider 类型: " + raw);
        }
    }

    private String normalizeEndpoint(ProviderType type, String raw) {
        try {
            URI uri = URI.create(requireText(raw, "provider 端点"));
            String scheme = uri.getScheme() == null
                ? ""
                : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!HTTP_SCHEMES.contains(scheme)
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw invalidEndpoint();
            }
            if (type.external() && !"https".equals(scheme)) {
                throw new ApiException(
                    ErrorCode.BAD_REQUEST, "外部 provider 端点必须使用 HTTPS");
            }
            return uri.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException invalid) {
            throw invalidEndpoint();
        }
    }

    private void assertExpectedVersion(ModelProviderConfig current, Long expectedVersion) {
        if (current == null && expectedVersion != null) {
            throw ApiException.conflict("新建 provider 不能携带 expectedVersion");
        }
        if (current != null && !Objects.equals(current.version(), expectedVersion)) {
            throw ApiException.conflict("provider 配置版本已变化，请刷新后重试");
        }
    }

    private String assertCredentialChangeConfirmed(String rawReason, boolean confirmed) {
        if (!confirmed) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "必须明确确认模型 provider 凭据变更的高危影响");
        }
        if (rawReason == null || rawReason.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "凭据变更原因不能为空");
        }
        String reason = rawReason.trim();
        if (reason.length() < 8) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "凭据变更原因至少需要 8 个字符，以便形成可核查审计证据");
        }
        if (reason.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "凭据变更原因不能超过 500 字");
        }
        return reason;
    }

    private void assertExpectedCredentialVersion(
            ModelProviderCredential current,
            Long expectedVersion) {
        if (current == null && expectedVersion != null) {
            throw ApiException.conflict("新建 provider 凭据不能携带 expectedVersion");
        }
        if (current != null && !Objects.equals(current.version(), expectedVersion)) {
            throw ApiException.conflict("provider 凭据版本已变化，请刷新后重试");
        }
    }

    private ModelProviderConfig requireProvider(String tenantId, String code) {
        return repository.findByTenantIdAndProviderCode(tenantId, code)
            .orElseThrow(() -> ApiException.notFound("模型 provider " + code));
    }

    private ModelProviderConfig disconnectedProvider(
            ModelProviderConfig current,
            Instant now,
            String actor) {
        return new ModelProviderConfig(
            current.id(),
            current.tenantId(),
            current.providerCode(),
            current.providerType(),
            current.endpointUri(),
            current.modelVersion(),
            "N",
            ProviderHealth.NOT_CONNECTED.name(),
            current.createdAt(),
            current.createdBy(),
            now,
            actor,
            current.version());
    }

    private ModelProviderGovernanceView toView(ModelProviderConfig config) {
        ModelProviderCredential credential = credentialRepository
            .findByTenantIdAndProviderCode(config.tenantId(), config.providerCode())
            .orElse(null);
        return ModelProviderGovernanceView.from(config, credential);
    }

    private ModelProviderConfig saveWithConflictTranslation(ModelProviderConfig config) {
        try {
            return repository.save(config);
        } catch (OptimisticLockingFailureException conflict) {
            throw new ApiException(
                ErrorCode.CONFLICT, "provider 配置版本已变化，请刷新后重试", conflict);
        }
    }

    private ModelProviderCredential saveCredentialWithConflictTranslation(
            ModelProviderCredential credential) {
        try {
            return credentialRepository.save(credential);
        } catch (OptimisticLockingFailureException conflict) {
            throw new ApiException(
                ErrorCode.CONFLICT, "provider 凭据版本已变化，请刷新后重试", conflict);
        }
    }

    private ApiException invalidEndpoint() {
        return new ApiException(
            ErrorCode.BAD_REQUEST, "provider 端点必须是纯净 HTTP(S) 绝对 URL");
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }

}
