package com.medkernel.engine.workflow;

/**
 * 统一通知查询条件。
 */
public record WorkflowNotificationFilter(
    WorkflowNotificationStatus status,
    WorkflowNotificationLevel level,
    String recipientId
) {
}
