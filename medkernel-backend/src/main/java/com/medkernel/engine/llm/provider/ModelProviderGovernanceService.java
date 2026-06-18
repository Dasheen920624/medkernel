package com.medkernel.engine.llm.provider;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.llm.eval.ModelEvalService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 模型 provider 治理服务（LLM-08 T13）。
 *
 * <p>由集成运维员（{@code llm.provider.manage}）配置 provider 接入；运行时解析在 {@link ModelProviderRegistry}。
 * 双形态门禁：运行侧内网（{@code HOSPITAL_RUNTIME}）禁止启用 B2 外部 provider（{@code ENG-LLM-009}）。
 * 上线评测门禁：启用 provider 前其 provider/版本须已通过医学回归评测（{@link ModelEvalService}），否则 {@code ENG-LLM-008} 阻断（LLM-07 T17）。
 * {@code credential_ref} 仅存引用，密钥不落库。
 */
@Service
public class ModelProviderGovernanceService {

    private final ModelProviderConfigRepository repository;
    private final DeploymentFormService deploymentForm;
    private final ModelEvalService evalService;
    private final ModelProviderRegistry registry;
    private final AuditRecorder auditRecorder;

    public ModelProviderGovernanceService(ModelProviderConfigRepository repository,
                                          DeploymentFormService deploymentForm,
                                          ModelEvalService evalService,
                                          ModelProviderRegistry registry,
                                          AuditRecorder auditRecorder) {
        this.repository = repository;
        this.deploymentForm = deploymentForm;
        this.evalService = evalService;
        this.registry = registry;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public ModelProviderConfig upsertProvider(String providerCode, ModelProviderUpsertRequest request) {
        String tenantId = requireCurrentTenant();
        String code = providerCode == null ? "" : providerCode.trim();
        ProviderType type = parseType(request.providerType());
        String endpointUri = request.endpointUri().trim();
        String credentialRef = normalizeOptional(request.credentialRef());
        String modelVersion = request.modelVersion().trim();
        boolean enabled = !Boolean.FALSE.equals(request.enabled());

        // 双形态门禁：运行侧内网禁止启用外部 provider（核心 §1/§8，患者数据不出境）。
        if (enabled && type.external() && !deploymentForm.allowsExternalProvider()) {
            throw new ApiException(ErrorCode.ENG_LLM_009,
                "运行侧内网形态禁止启用外部模型 provider " + code);
        }

        // 上线评测门禁：启用即上线，provider/版本须已过医学回归评测 PASSED（LLM-07 T17，铁律 #1/#4）。
        if (enabled && !evalService.isClearedForGoLive(tenantId, code, modelVersion)) {
            throw new ApiException(ErrorCode.ENG_LLM_008,
                "模型 provider " + code + " 版本 " + modelVersion + " 未通过医学回归评测，禁止上线");
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        Optional<ModelProviderConfig> existing = repository.findByTenantIdAndProviderCode(tenantId, code);
        String status = existing
            .filter(config -> !connectionMaterialChanged(
                config, type, endpointUri, credentialRef, modelVersion))
            .map(ModelProviderConfig::status)
            .orElse("NOT_CONNECTED");
        ModelProviderConfig saved = repository.save(new ModelProviderConfig(
            existing.map(ModelProviderConfig::id).orElse(null),
            tenantId,
            code,
            type.name(),
            endpointUri,
            credentialRef,
            modelVersion,
            enabled ? "Y" : "N",
            status,
            existing.map(ModelProviderConfig::createdAt).orElse(now),
            existing.map(ModelProviderConfig::createdBy).orElse(actor),
            now,
            actor));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_provider", code, "保存模型 provider " + code);
        return saved;
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
        ModelProviderConfig checked = repository.save(new ModelProviderConfig(
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
            actor));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_provider", code,
            "探测模型 provider " + code + " status=" + health.name());
        return checked;
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

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
