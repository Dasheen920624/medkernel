package com.medkernel.engine.llm;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
  * 模型网关调用任务实体。
  *
  * <p>记录大模型或B0基线回退每一次能力抽取的详细上下文、耗时、运行模式以及审计依据。
  */
@Table("model_capability_task")
public record ModelCapabilityTask(
    @Id Long id,
    @Column("task_id") String taskId,
    @Column("tenant_id") String tenantId,
    @Column("capability_code") String capabilityCode,
    @Column("input_hash") String inputHash,
    @Column("input_summary") String inputSummary,
    @Column("output_content") String outputContent,
    @Column("model_mode") String modelMode,
    @Column("model_version") String modelVersion,
    @Column("prompt_version") String promptVersion,
    @Column("tool_version") String toolVersion,
    @Column("source_citations") String sourceCitations,
    Double confidence,
    @Column("risk_level") String riskLevel,
    @Column("fallback_used") Boolean fallbackUsed,
    @Column("fallback_reason") String fallbackReason,
    @Column("time_cost_ms") Long timeCostMs,
    String status,
    @Column("trace_id") String traceId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
