package com.medkernel.engine.evaluation;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 评估运行实体。
 *
 * <p>表示一次人工抽检、上游结果或批量导入的受控事实入库批次，保存来源、上下文引用、输入摘要和运行状态。
 */
@Table("evaluation_run")
public record EvaluationRun(
    @Id Long id,
    @Column("run_id") String runId,
    @Column("tenant_id") String tenantId,
    @Column("run_code") String runCode,
    @Column("run_type") EvaluationRunType runType,
    @Column("source_event_id") String sourceEventId,
    @Column("context_snapshot_id") String contextSnapshotId,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("scenario_code") String scenarioCode,
    @Column("runtime_release_id") String runtimeReleaseId,
    @Column("input_digest") String inputDigest,
    EvaluationRunStatus status,
    @Column("error_code") String errorCode,
    @Column("occurred_at") Instant occurredAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
