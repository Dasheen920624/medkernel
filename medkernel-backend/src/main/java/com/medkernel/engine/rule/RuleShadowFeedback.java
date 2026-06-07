package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 影子运行命中后的人工复核事实，用于统计真实命中与误报。
 */
@Table("rule_shadow_feedback")
public record RuleShadowFeedback(
    @Id Long id,
    @Column("feedback_id") String feedbackId,
    @Column("tenant_id") String tenantId,
    @Column("execution_id") String executionId,
    @Column("rule_id") String ruleId,
    @Column("version_id") String versionId,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    RuleShadowFeedbackDecision decision,
    String reason,
    @Column("assessed_by") String assessedBy,
    @Column("assessed_at") Instant assessedAt,
    @Column("created_at") Instant createdAt,
    @Column("trace_id") String traceId
) {}
