package com.medkernel.engine.knowledge.discovery;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;

/**
 * 过期知识复核任务。
 *
 * <p>来源废止或复审超期只触发任务和审计依据，不直接撤回或替换临床权威版本。
 */
@Table("expiry_task")
public record ExpiryTask(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("task_key") String taskKey,
    @Column("diff_id") Long diffId,
    @Column("identity_id") Long identityId,
    @Column("version_id") Long versionId,
    @Column("task_type") ExpiryTaskType taskType,
    @Column("status") ExpiryTaskStatus status,
    @Column("risk_level") KnowledgeRiskLevel riskLevel,
    @Column("reason") String reason,
    @Column("review_due_at") Instant reviewDueAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
