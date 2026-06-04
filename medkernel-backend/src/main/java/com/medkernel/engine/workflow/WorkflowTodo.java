package com.medkernel.engine.workflow;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 统一协同待办实体。
 */
@Table("mk_engine_workflow_todo")
public record WorkflowTodo(
    @Id Long id,
    @Column("todo_id") String todoId,
    @Column("tenant_id") String tenantId,
    @Column("source_type") WorkflowTodoSourceType sourceType,
    @Column("source_id") String sourceId,
    String title,
    String summary,
    WorkflowPriority priority,
    WorkflowTodoStatus status,
    @Column("assignee_id") String assigneeId,
    @Column("assignee_role") String assigneeRole,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("due_at") Instant dueAt,
    @Column("deep_link") String deepLink,
    @Column("completion_reason") String completionReason,
    @Column("completed_at") Instant completedAt,
    @Column("completed_by") String completedBy,
    @Column("transferred_to") String transferredTo,
    @Column("transfer_reason") String transferReason,
    @Column("trace_id") String traceId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
