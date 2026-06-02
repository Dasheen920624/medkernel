package com.medkernel.engine.terminology;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 高危近似术语规则。
 *
 * <p>规则由迁移种子写入，可按租户覆盖；运行时只解释规则类型，不在代码里散落临床常量。
 * 命中后候选强制 HIGH，后续沿用既有批量拒绝与逐条二次确认门禁。
 */
@Table("mk_term_high_risk_rule")
public record HighRiskRule(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("rule_code") String ruleCode,
    @Column("rule_type") HighRiskRuleType ruleType,
    @Column("category") TermCategory category,
    @Column("left_terms") String leftTerms,
    @Column("right_terms") String rightTerms,
    @Column("unit_terms") String unitTerms,
    @Column("scale_ratio") Double scaleRatio,
    @Column("evidence_text") String evidenceText,
    @Column("status") HighRiskRuleStatus status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
