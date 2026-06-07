package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 阻断或强提醒动作的人工越权事实。
 */
@Table("rule_override_log")
public record RuleOverrideLog(
    @Id Long id,
    @Column("override_id") String overrideId,
    @Column("tenant_id") String tenantId,
    @Column("execution_id") String executionId,
    @Column("rule_id") String ruleId,
    @Column("version_id") String versionId,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("action_code") RuleActionCode actionCode,
    @Column("override_reason") String overrideReason,
    @Column("overridden_by") String overriddenBy,
    @Column("overridden_at") Instant overriddenAt,
    @Column("created_at") Instant createdAt,
    @Column("trace_id") String traceId
) {}
