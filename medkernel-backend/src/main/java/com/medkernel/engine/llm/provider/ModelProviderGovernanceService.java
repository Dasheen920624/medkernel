package com.medkernel.engine.llm.provider;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.llm.eval.ModelEvalService;
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
 * <p>由集成运维员（{@code llm.provider.manage}）配置 provider 接入；运行时解析在 {@link ModelProviderRegistry}。
 * 配置写入与高危启停相互分离：本服务的配置入口始终保存为停用，避免编辑连接参数时意外上线。
 * {@code credential_ref} 仅存环境变量键名，密钥不落库；关系库乐观锁防止配置、探活与启停相互覆盖。
 */
@Service
public class ModelProviderGovernanceService {

    private static final Set<String> HTTP_SCHEMES = Set.of("http", "https");
    private static final Pattern ENV_KEY = Pattern.compile("[A-Z][A-Z0-9_]{2,127}");
    private static final String HIGH_RISK_RESOURCE_TYPE = "model_provider";

    private final ModelProviderConfigRepository repository;
    private final DeploymentFormService deploymentForm;
    private final ModelEvalService evalService;
    private final ModelProviderRegistry registry;
    private final AuditRecorder auditRecorder;
    private final HighRiskChangeGuard highRiskGuard;

    public ModelProviderGovernanceService(ModelProviderConfigRepository repository,
                                          DeploymentFormService deploymentForm,
                                          ModelEvalService evalService,
                                          ModelProviderRegistry registry,
                                          AuditRecorder auditRecorder,
                                          HighRiskChangeGuard highRiskGuard) {
        this.repository = repository;
        this.deploymentForm = deploymentForm;
        this.evalService = evalService;
        this.registry = registry;
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
        String credentialRef = normalizeCredentialRef(type, request.credentialRef());
        String modelVersion = requireText(request.modelVersion(), "模型版本");
        boolean changed = current == null || connectionMaterialChanged(
            current, type, endpointUri, credentialRef, modelVersion);

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelProviderConfig saved = saveWithConflictTranslation(new ModelProviderConfig(
            current == null ? null : current.id(),
            tenantId,
            code,
            type.name(),
            endpointUri,
            credentialRef,
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
        return ModelProviderGovernanceView.from(config);
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
            current.credentialRef(),
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
        String tenantId = requireCurrentTenant();
        String code = requireText(providerCode, "provider 编码");
        highRiskGuard.assertHighRiskAllowed(HIGH_RISK_RESOURCE_TYPE, code);
        ModelProviderConfig current = repository.findByTenantIdAndProviderCode(tenantId, code)
            .orElseThrow(() -> ApiException.notFound("模型 provider " + code));
        assertExpectedVersion(current, request.expectedVersion());

        if (current.enabled() == enabled) {
            return ModelProviderGovernanceView.from(current);
        }
        if (enabled) {
            assertProviderCanBeEnabled(tenantId, code, current);
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelProviderConfig saved = saveWithConflictTranslation(new ModelProviderConfig(
            current.id(),
            current.tenantId(),
            current.providerCode(),
            current.providerType(),
            current.endpointUri(),
            current.credentialRef(),
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
            (enabled ? "启用" : "停用") + "模型 provider " + code + "：" + reason);
        return ModelProviderGovernanceView.from(saved);
    }

    private String assertActivationConfirmed(ModelProviderActivationRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirmedHighRisk())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "必须明确确认模型 provider 启停的高危影响");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "启停原因不能为空");
        }
        String reason = request.reason().trim();
        if (reason.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "启停原因不能超过 500 字");
        }
        return reason;
    }

    private void assertProviderCanBeEnabled(
            String tenantId,
            String code,
            ModelProviderConfig current) {
        if (!ProviderHealth.HEALTHY.name().equals(current.status())) {
            throw ApiException.conflict("provider 未通过当前真实健康检查，禁止启用");
        }
        ProviderType type = parseType(current.providerType());
        if (type.external() && !deploymentForm.allowsExternalProvider()) {
            throw new ApiException(ErrorCode.ENG_LLM_009);
        }
        if (!evalService.isClearedForGoLive(tenantId, code, current.modelVersion())) {
            throw new ApiException(ErrorCode.ENG_LLM_008);
        }
    }

    private boolean connectionMaterialChanged(ModelProviderConfig current,
                                              ProviderType type,
                                              String endpointUri,
                                              String credentialRef,
                                              String modelVersion) {
        return !type.name().equals(current.providerType())
            || !endpointUri.equals(current.endpointUri())
            || !Objects.equals(credentialRef, current.credentialRef())
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

    private String normalizeCredentialRef(ProviderType type, String raw) {
        String credentialRef = normalizeOptional(raw);
        if (credentialRef == null) {
            if (type.external()) {
                throw new ApiException(
                    ErrorCode.BAD_REQUEST, "外部 provider 必须配置凭据环境变量引用");
            }
            return null;
        }
        if (!ENV_KEY.matcher(credentialRef).matches()) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST, "provider 凭据引用必须是合法的环境变量键名");
        }
        return credentialRef;
    }

    private void assertExpectedVersion(ModelProviderConfig current, Long expectedVersion) {
        if (current == null && expectedVersion != null) {
            throw ApiException.conflict("新建 provider 不能携带 expectedVersion");
        }
        if (current != null && !Objects.equals(current.version(), expectedVersion)) {
            throw ApiException.conflict("provider 配置版本已变化，请刷新后重试");
        }
    }

    private ModelProviderConfig saveWithConflictTranslation(ModelProviderConfig config) {
        try {
            return repository.save(config);
        } catch (OptimisticLockingFailureException conflict) {
            throw new ApiException(
                ErrorCode.CONFLICT, "provider 配置版本已变化，请刷新后重试", conflict);
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

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
