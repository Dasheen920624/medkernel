package com.medkernel.engine.pathway;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 患者路径节点的关键时钟事实。
 *
 * <p>记录节点开始、基准事件、目标/最早/最晚时限、完成、状态和可选质控指标关联，用于路径执行时效追踪。
 */
@Table("clinical_clock")
public record ClinicalClock(
    @Id Long id,
    @Column("clock_id") String clockId,
    @Column("tenant_id") String tenantId,
    @Column("patient_pathway_id") String patientPathwayId,
    @Column("node_code") String nodeCode,
    @Column("metric_code") String metricCode,
    @Column("started_at") Instant startedAt,
    @Column("due_at") Instant dueAt,
    @Column("completed_at") Instant completedAt,
    ClinicalClockStatus status,
    @Column("baseline_event") String baselineEvent,
    @Column("baseline_at") Instant baselineAt,
    @Column("min_due_at") Instant minDueAt,
    @Column("target_due_at") Instant targetDueAt,
    @Column("max_due_at") Instant maxDueAt,
    @Column("escalation_level") ClinicalClockEscalationLevel escalationLevel,
    @Column("escalation_policy_json") String escalationPolicyJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
