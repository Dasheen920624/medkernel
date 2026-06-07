package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 规则执行日志实体（GA-ENG-API-05 真实执行与试运行执行的事实记录）。
 *
 * <p>{@code execution_id} 全局唯一；仅保存输入 SHA-256 摘要与解释快照，不落患者完整上下文，
 * 通过 {@link com.medkernel.shared.observability.DiagnoseResponseAssembler} 还原可解释诊断。
 */
@Table("rule_execution_log")
public record RuleExecutionLog(
    @Id Long id,
    @Column("execution_id") String executionId,
    @Column("tenant_id") String tenantId,
    @Column("rule_id") String ruleId,
    @Column("version_id") String versionId,
    @Column("trigger_point") String triggerPoint,
    @Column("event_id") String eventId,
    @Column("actor_user_id") String actorUserId,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("semantic_key") String semanticKey,
    @Column("input_digest") String inputDigest,
    Boolean hit,
    RuleRiskLevel severity,
    @Column("actions_json") String actionsJson,
    @Column("explanation_json") String explanationJson,
    RuleExecutionStatus status,
    @Column("error_code") String errorCode,
    @Column("error_class") String errorClass,
    @Column("deduplicated_from_execution_id") String deduplicatedFromExecutionId,
    @Column("executed_at") Instant executedAt,
    @Column("created_at") Instant createdAt,
    @Column("trace_id") String traceId
) {}
