package com.medkernel.engine.llm.egress;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * 出域治理管理服务：维护能力码出域字段白名单与高敏出域责任确认。
 *
 * <p>运行时拦截在 {@link ModelEgressGuard}；本服务是管理面，由医疗引擎运营员（{@code llm.egress.manage}）
 * 配置白名单并确认当前操作用途，全程租户隔离 + 审计留痕。
 */
@Service
public class ModelEgressGovernanceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> SENSITIVITY_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> DESENSITIZATION_OPERATORS = Set.of("MASK", "MASK_ALL", "GENERALIZE", "NULLIFY", "NONE");

    private final ModelEgressWhitelistRepository whitelistRepo;
    private final ModelEgressConfirmationRepository confirmationRepo;
    private final AuditRecorder auditRecorder;

    public ModelEgressGovernanceService(ModelEgressWhitelistRepository whitelistRepo,
                                        ModelEgressConfirmationRepository confirmationRepo,
                                        AuditRecorder auditRecorder) {
        this.whitelistRepo = whitelistRepo;
        this.confirmationRepo = confirmationRepo;
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
        String confirmationThreshold = request.confirmationThresholdLevel() == null
            ? "HIGH" : request.confirmationThresholdLevel().trim().toUpperCase(Locale.ROOT);
        if (!SENSITIVITY_LEVELS.contains(confirmationThreshold)) {
            throw new ApiException(
                ErrorCode.ENG_LLM_007,
                "非法的出域责任确认阈值: " + request.confirmationThresholdLevel());
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        Optional<ModelEgressWhitelist> existing = whitelistRepo.findByTenantIdAndCapabilityCode(tenantId, code);
        List<String> allowedFields = normalizeFields(request.allowedFields());
        ModelEgressWhitelist saved = whitelistRepo.save(new ModelEgressWhitelist(
            existing.map(ModelEgressWhitelist::id).orElse(null),
            tenantId,
            code,
            toJsonArray(allowedFields),
            sensitivity,
            toRulesJson(request.desensitizationRules(), allowedFields),
            confirmationThreshold,
            "Y",
            existing.map(ModelEgressWhitelist::createdAt).orElse(now),
            existing.map(ModelEgressWhitelist::createdBy).orElse(actor),
            now,
            actor));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_egress_whitelist", code,
            "保存模型出域白名单 " + code);
        return saved;
    }

    /**
     * 记录当前获授权操作者对脱敏后载荷用途的责任确认。
     */
    @Transactional
    public ModelEgressConfirmation confirmEgress(ModelEgressConfirmationRequest request) {
        String tenantId = requireCurrentTenant();
        String code = normalize(request.capabilityCode());
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelEgressConfirmation saved = confirmationRepo.save(new ModelEgressConfirmation(
            null,
            tenantId,
            code,
            request.payloadHash().trim(),
            request.purpose().trim(),
            actor,
            now,
            now,
            actor,
            now,
            actor));
        auditRecorder.record(
            AuditAction.UPDATE,
            "mk_llm_egress_confirmation",
            request.payloadHash().trim(),
            "确认模型出域用途 " + code);
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

    private List<String> normalizeFields(List<String> fields) {
        if (fields == null) {
            throw new ApiException(ErrorCode.ENG_LLM_006, "出域字段白名单不能为空");
        }
        List<String> normalized = fields.stream()
            .filter(field -> field != null && !field.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
        if (normalized.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_LLM_006, "出域字段白名单不能为空");
        }
        return normalized;
    }

    private String toJsonArray(List<String> fields) {
        var array = OBJECT_MAPPER.createArrayNode();
        fields.forEach(array::add);
        return array.toString();
    }

    private String toRulesJson(Map<String, String> rules, List<String> allowedFields) {
        Map<String, String> safeRules = new LinkedHashMap<>();
        Set<String> allowed = Set.copyOf(allowedFields);
        if (rules != null) {
            for (Map.Entry<String, String> entry : rules.entrySet()) {
                String field = entry.getKey() == null ? "" : entry.getKey().trim();
                if (field.isBlank() || !allowed.contains(field)) {
                    throw new ApiException(ErrorCode.ENG_LLM_006, "脱敏规则字段不在出域白名单内: " + entry.getKey());
                }
                String operator = entry.getValue() == null ? "" : entry.getValue().trim().toUpperCase(Locale.ROOT);
                if (!DESENSITIZATION_OPERATORS.contains(operator)) {
                    throw new ApiException(ErrorCode.ENG_LLM_006, "非法的脱敏算子: " + entry.getValue());
                }
                safeRules.put(field, operator);
            }
        }
        return OBJECT_MAPPER.valueToTree(safeRules).toString();
    }
}
