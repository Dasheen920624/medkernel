package com.medkernel.engine.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * LLM-04 prompt/tool/model 版本治理服务。
 *
 * <p>版本包发布、回滚和导出均只处理版本元数据与 hash，不保存正文或密钥。
 */
@Service
public class ModelVersionGovernanceService {

    private final ModelVersionBundleRepository repository;
    private final AuditRecorder auditRecorder;

    public ModelVersionGovernanceService(ModelVersionBundleRepository repository,
                                         AuditRecorder auditRecorder) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public ModelVersionBundleResponse publish(ModelVersionBundleRequest request) {
        String tenantId = requireCurrentTenant();
        String actor = RequestContext.currentUserId().orElse("system");
        String capability = normalizeCapability(request.capabilityCode());
        String promptVersion = requireText(request.promptVersion(), "prompt_version");
        String promptContent = requireText(request.promptContent(), "prompt_content");
        String toolVersion = requireText(request.toolVersion(), "tool_version");
        String toolContract = requireText(request.toolContract(), "tool_contract");
        String modelVersion = requireText(request.modelVersion(), "model_version");
        String modelDescriptor = requireText(request.modelDescriptor(), "model_descriptor");
        Instant now = Instant.now();
        repository.retireActive(tenantId, capability, actor, now);
        ModelVersionBundle saved = repository.save(new ModelVersionBundle(
            null,
            tenantId,
            capability,
            promptVersion,
            sha256(promptContent),
            toolVersion,
            sha256(toolContract),
            modelVersion,
            sha256(modelDescriptor),
            "ACTIVE",
            now,
            null,
            now,
            actor,
            now,
            actor));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_model_version_bundle",
            String.valueOf(saved.id()), "发布模型版本三元组 " + capability);
        return ModelVersionBundleResponse.from(saved);
    }

    @Transactional
    public ModelVersionBundleResponse rollback(String capabilityCode, Long bundleId) {
        String tenantId = requireCurrentTenant();
        String actor = RequestContext.currentUserId().orElse("system");
        String capability = normalizeCapability(capabilityCode);
        ModelVersionBundle target = repository.findById(bundleId)
            .filter(bundle -> tenantId.equals(bundle.tenantId()) && capability.equals(bundle.capabilityCode()))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "模型版本包不存在"));
        Instant now = Instant.now();
        repository.retireActive(tenantId, capability, actor, now);
        repository.activateBundle(bundleId, tenantId, capability, actor, now);
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_model_version_bundle",
            String.valueOf(bundleId), "回滚模型版本三元组 " + capability);
        return ModelVersionBundleResponse.activeFrom(new ModelVersionBundle(
            target.id(), target.tenantId(), target.capabilityCode(), target.promptVersion(), target.promptHash(),
            target.toolVersion(), target.toolHash(), target.modelVersion(), target.modelHash(), "ACTIVE",
            now, null, target.createdAt(), target.createdBy(), now, actor));
    }

    @Transactional(readOnly = true)
    public ModelVersionBundleResponse active(String capabilityCode) {
        String tenantId = requireCurrentTenant();
        String capability = normalizeCapability(capabilityCode);
        return repository.findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(tenantId, capability, "ACTIVE")
            .map(ModelVersionBundleResponse::from)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "模型版本包不存在"));
    }

    @Transactional(readOnly = true)
    public ModelVersionExportResponse export(String capabilityCode) {
        String tenantId = requireCurrentTenant();
        String capability = normalizeCapability(capabilityCode);
        return new ModelVersionExportResponse(
            tenantId,
            capability,
            repository.findByTenantIdAndCapabilityCodeOrderByIdDesc(tenantId, capability).stream()
                .map(ModelVersionBundleResponse::from)
                .toList());
    }

    @Transactional(readOnly = true)
    public ModelVersionTriple activeTripleOrBaseline(String tenantId, String capabilityCode) {
        String capability = normalizeCapability(capabilityCode);
        return repository.findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(tenantId, capability, "ACTIVE")
            .map(bundle -> new ModelVersionTriple(
                bundle.promptVersion(),
                bundle.toolVersion(),
                bundle.modelVersion()))
            .orElseGet(ModelVersionTriple::baseline);
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String normalizeCapability(String capabilityCode) {
        if (capabilityCode == null || capabilityCode.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "能力代码不能为空");
        }
        return capabilityCode.trim().toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, fieldName + " 不能为空");
        }
        return value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "模型版本指纹计算失败", ex);
        }
    }
}
