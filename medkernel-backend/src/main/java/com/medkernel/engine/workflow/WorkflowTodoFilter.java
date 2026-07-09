package com.medkernel.engine.workflow;

/**
 * 统一待办查询条件。
 */
public record WorkflowTodoFilter(
    WorkflowTodoStatus status,
    WorkflowPriority priority,
    WorkflowTodoSourceType sourceType,
    String assigneeId,
    String patientId,
    String orgUnitId,
    String sourceId
) {

    public WorkflowTodoFilter(
            WorkflowTodoStatus status,
            WorkflowPriority priority,
            WorkflowTodoSourceType sourceType,
            String assigneeId,
            String patientId) {
        this(status, priority, sourceType, assigneeId, patientId, null, null);
    }

    public WorkflowTodoFilter(
            WorkflowTodoStatus status,
            WorkflowPriority priority,
            WorkflowTodoSourceType sourceType,
            String assigneeId,
            String patientId,
            String orgUnitId) {
        this(status, priority, sourceType, assigneeId, patientId, orgUnitId, null);
    }
}
