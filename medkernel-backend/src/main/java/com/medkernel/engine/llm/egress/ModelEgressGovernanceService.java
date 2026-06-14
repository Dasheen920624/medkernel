package com.medkernel.engine.llm.egress;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 出域治理管理服务（LLM-03）：维护能力码出域字段白名单与高敏出域审批决定。
 *
 * <p>运行时拦截在 {@link ModelEgressGuard}；本服务是管理面，由集成运维员（{@code llm.egress.manage}）
 * 配置白名单与裁定审批，全程租户隔离 + 审计留痕。
 */
@Service
public class ModelEgressGovernanceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> SENSITIVITY_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> APPROVAL_DECISIONS = Set.of("APPROVED", "REJECTED");

    private final ModelEgressWhitelistRepository whitelistRepo;
    private final ModelEgressApprovalRepository approvalRepo;
    private final AuditRecorder auditRecorder;

    public ModelEgressGovernanceService(ModelEgressWhitelistRepository whitelistRepo,
                                        ModelEgressApprovalRepository approvalRepo,
                                        AuditRecorder auditRecorder) {
        this.whitelistRepo = whitelistRepo;
        this.approvalRepo = approvalRepo;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 新增或更新指定能力码的出域字段白名单。
     */
    @Transactional
    public ModelEgressWhitelist upsertWhitelist(String capabilityCode, ModelEgressWhitelistUpsertRequest request) {
        String tenantId = requireCurrentTenant();
        String code = normalize(capabilityCode);
        String sensitivity = request.sensitivityLevel() == null
            ? "" : request.sensitivityLevel().trim().toUpperCase(Locale.ROOT);
        if (!SENSITIVITY_LEVELS.contains(sensitivity)) {
            throw new ApiException(ErrorCode.ENG_LLM_006, "非法的出域敏感级别: " + request.sensitivityLevel());
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        Optional<ModelEgressWhitelist> existing = whitelistRepo.findByTenantIdAndCapabilityCode(tenantId, code);
        ModelEgressWhitelist saved = whitelistRepo.save(new ModelEgressWhitelist(
            existing.map(ModelEgressWhitelist::id).orElse(null),
            tenantId,
            code,
            toJsonArray(request.allowedFields()),
            sensitivity,
            existing.map(ModelEgressWhitelist::createdAt).orElse(now),
            existing.map(ModelEgressWhitelist::createdBy).orElse(actor),
            now,
            actor));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_egress_whitelist", code,
            "保存模型出域白名单 " + code);
        return saved;
    }

    /**
     * 记录一条高敏出域审批裁定（APPROVED / REJECTED）。
     */
    @Transactional
    public ModelEgressApproval decideApproval(ModelEgressApprovalRequest request) {
        String tenantId = requireCurrentTenant();
        String code = normalize(request.capabilityCode());
        String decision = request.decision() == null
            ? "" : request.decision().trim().toUpperCase(Locale.ROOT);
        if (!APPROVAL_DECISIONS.contains(decision)) {
            throw new ApiException(ErrorCode.ENG_LLM_007, "非法的出域审批裁定: " + request.decision());
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelEgressApproval saved = approvalRepo.save(new ModelEgressApproval(
            null, tenantId, code, request.payloadHash().trim(), decision,
            actor, now, now, actor, now, actor));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_egress_approval", request.payloadHash().trim(),
            "裁定模型出域审批 " + code + " -> " + decision);
        return saved;
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String toJsonArray(List<String> fields) {
        var array = OBJECT_MAPPER.createArrayNode();
        fields.forEach(array::add);
        return array.toString();
    }
}
