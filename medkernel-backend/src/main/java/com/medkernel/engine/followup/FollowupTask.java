package com.medkernel.engine.followup;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 随访任务实体。
 */
@Table("followup_task")
public record FollowupTask(
    @Id Long id,
    @Column("task_id") String taskId,
    @Column("tenant_id") String tenantId,
    @Column("plan_id") String planId,
    @Column("task_type") FollowupTaskType taskType,
    @Column("due_date") Instant dueDate,
    FollowupTaskStatus status,
    @Column("executor_id") String executorId,
    @Column("executor_type") String executorType,
    @Column("idempotency_key") String idempotencyKey,
    @Column("clinical_clock_id") String clinicalClockId,
    @Column("questionnaire_template_id") String questionnaireTemplateId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public FollowupTask(
            Long id,
            String taskId,
            String tenantId,
            String planId,
            FollowupTaskType taskType,
            Instant dueDate,
            FollowupTaskStatus status,
            String executorId,
            String executorType,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(id, taskId, tenantId, planId, taskType, dueDate, status, executorId, executorType, null, null, null,
            createdAt, createdBy, updatedAt, updatedBy, traceId);
    }

    public FollowupTask(
            Long id,
            String taskId,
            String tenantId,
            String planId,
            FollowupTaskType taskType,
            Instant dueDate,
            FollowupTaskStatus status,
            String executorId,
            String executorType,
            String idempotencyKey,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(id, taskId, tenantId, planId, taskType, dueDate, status, executorId, executorType, idempotencyKey, null,
            null,
            createdAt, createdBy, updatedAt, updatedBy, traceId);
    }

    public FollowupTask(
            Long id,
            String taskId,
            String tenantId,
            String planId,
            FollowupTaskType taskType,
            Instant dueDate,
            FollowupTaskStatus status,
            String executorId,
            String executorType,
            String idempotencyKey,
            String clinicalClockId,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(
            id, taskId, tenantId, planId, taskType, dueDate, status, executorId, executorType,
            idempotencyKey, clinicalClockId, null, createdAt, createdBy, updatedAt, updatedBy, traceId
        );
    }
}
