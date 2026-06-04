package com.medkernel.engine.workflow;

import java.time.Instant;

import com.medkernel.engine.followup.FollowupTaskStatus;
import com.medkernel.engine.followup.FollowupTaskType;

/**
 * 随访任务投影为统一待办的只读行。
 */
public record FollowupWorkflowTodoRow(
    String taskId,
    String planId,
    FollowupTaskType taskType,
    FollowupTaskStatus status,
    String patientId,
    String encounterId,
    Instant dueAt,
    String executorId,
    String executorType,
    String traceId,
    Instant createdAt
) {
}
