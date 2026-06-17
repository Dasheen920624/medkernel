package com.medkernel.engine.llm.egress;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 模型外调出域字段白名单实体（LLM-03 FR-1）。
 *
 * <p>声明指定租户、指定能力码在 B2 外调时允许出域的字段清单（JSON 字符串数组）与出域敏感级别。
 * 敏感级 {@code HIGH} 的出域须经审批方可放行（{@link ModelEgressApproval}）。
 * OPT-09 在同一策略记录上集中维护字段级脱敏规则、审批阈值和不可关闭护栏，避免各出域点重复实现。
 */
@Table("mk_llm_egress_whitelist")
public record ModelEgressWhitelist(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("capability_code") String capabilityCode,
    @Column("allowed_fields") String allowedFields,
    @Column("sensitivity_level") String sensitivityLevel, // LOW, MEDIUM, HIGH
    @Column("desensitization_rules") String desensitizationRules,
    @Column("approval_threshold_level") String approvalThresholdLevel,
    @Column("guardrail_locked_flag") String guardrailLockedFlag,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    public ModelEgressWhitelist(
            Long id,
            String tenantId,
            String capabilityCode,
            String allowedFields,
            String sensitivityLevel,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        this(id, tenantId, capabilityCode, allowedFields, sensitivityLevel,
            "{}", "HIGH", "Y", createdAt, createdBy, updatedAt, updatedBy);
    }

    public ModelEgressWhitelist {
        desensitizationRules = blankToDefault(desensitizationRules, "{}");
        approvalThresholdLevel = blankToDefault(approvalThresholdLevel, "HIGH");
        guardrailLockedFlag = blankToDefault(guardrailLockedFlag, "Y");
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
