package com.medkernel.engine.workflow;

import java.time.Instant;

/**
 * 统一协同待办响应 DTO。
 */
public record WorkflowTodoResponse(
    String todoId,
    WorkflowTodoSourceType sourceType,
    String sourceId,
    String title,
    String summary,
    WorkflowPriority priority,
    WorkflowTodoStatus status,
    String assigneeId,
    String assigneeRole,
    String patientId,
    String encounterId,
    Instant dueAt,
    String deepLink,
    String completionReason,
    Instant completedAt,
    String completedBy,
    String traceId
) {
    static WorkflowTodoResponse from(WorkflowTodo todo) {
        return new WorkflowTodoResponse(
            todo.todoId(),
            todo.sourceType(),
            todo.sourceId(),
            todo.title(),
            todo.summary(),
            todo.priority(),
            todo.status(),
            todo.assigneeId(),
            todo.assigneeRole(),
            todo.patientId(),
            todo.encounterId(),
            todo.dueAt(),
            todo.deepLink(),
            todo.completionReason(),
            todo.completedAt(),
            todo.completedBy(),
            todo.traceId());
    }
}
