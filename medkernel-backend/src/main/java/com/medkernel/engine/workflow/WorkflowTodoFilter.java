package com.medkernel.engine.workflow;

/**
 * 统一待办查询条件。
 */
public record WorkflowTodoFilter(
    WorkflowTodoStatus status,
    WorkflowPriority priority,
    WorkflowTodoSourceType sourceType,
    String assigneeId,
    String patientId
) {
}
