package com.medkernel.compliance.masking;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * SYS-06 脱敏规则持久化实体。
 */
@Table("mk_compliance_masking_rule")
public record MaskingRule(
    @Id Long id,
    @Column("rule_id") String ruleId,
    @Column("tenant_id") String tenantId,
    @Column("resource_type") String resourceType,
    @Column("field_name") String fieldName,
    @Column("scenario_code") String scenarioCode,
    @Column("strategy") String strategy,
    @Column("mask_char") String maskChar,
    @Column("prefix_keep") Integer prefixKeep,
    @Column("suffix_keep") Integer suffixKeep,
    @Column("status") String status,
    @Column("version") Long version,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {

    public MaskingRule withId(Long newId) {
        return new MaskingRule(newId, ruleId, tenantId, resourceType, fieldName, scenarioCode, strategy,
            maskChar, prefixKeep, suffixKeep, status, version, createdAt, createdBy, updatedAt, updatedBy, traceId);
    }
}
