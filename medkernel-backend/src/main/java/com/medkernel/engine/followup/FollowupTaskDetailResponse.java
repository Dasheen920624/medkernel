package com.medkernel.engine.followup;

import java.time.Instant;

/**
 * 随访任务详情响应数据契约 (GA-ENG-API-09)。
 */
public record FollowupTaskDetailResponse(
    String taskId,
    String planId,
    FollowupTaskType taskType,
    Instant dueDate,
    FollowupTaskStatus status,
    String executorId,
    String executorType
) {
    public FollowupTaskDetailResponse(
            String taskId,
            FollowupTaskType taskType,
            Instant dueDate,
            FollowupTaskStatus status) {
        this(taskId, null, taskType, dueDate, status, null, null);
    }
}
