package com.medkernel.engine.evaluation;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 整改任务实体。
 *
 * <p>保存质量问题的责任科室、处理人、整改期限、提交内容、证据引用和任务状态。
 */
@Table("rectification_task")
public record RectificationTask(
    @Id Long id,
    @Column("task_id") String taskId,
    @Column("tenant_id") String tenantId,
    @Column("finding_id") String findingId,
    @Column("responsible_department_id") String responsibleDepartmentId,
    @Column("assignee_user_id") String assigneeUserId,
    RectificationTaskStatus status,
    @Column("due_at") Instant dueAt,
    @Column("rectification_summary") String rectificationSummary,
    @Column("evidence_ref") String evidenceRef,
    @Column("submitted_at") Instant submittedAt,
    @Column("submitted_by") String submittedBy,
    @Column("closed_at") Instant closedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
