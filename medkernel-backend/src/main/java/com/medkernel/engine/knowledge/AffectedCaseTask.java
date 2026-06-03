package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 知识失效后的影响处置任务。
 *
 * <p>当前 B0 只在有真实索引时记录患者 / 路径目标；否则落知识版本、配置包和同步范围级任务，
 * 避免编造病例清单，同时保证安全风险不会等到下一次自然触发才被处理。
 */
@Table("mk_knowledge_affected_case_task")
public record AffectedCaseTask(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("task_key") String taskKey,
    @Column("invalidation_id") Long invalidationId,
    @Column("identity_id") Long identityId,
    @Column("version_id") Long versionId,
    @Column("task_type") AffectedCaseTaskType taskType,
    @Column("status") AffectedCaseTaskStatus status,
    @Column("target_type") AffectedCaseTargetType targetType,
    @Column("target_ref") String targetRef,
    @Column("reason") String reason,
    @Column("due_at") Instant dueAt,
    @Column("assigned_to") String assignedTo,
    @Column("trace_id") String traceId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
