package com.medkernel.engine.followup;

/**
 * 随访任务状态。
 */
public enum FollowupTaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ABNORMAL_RETURN,
    OVERDUE,
    CANCELLED
}
