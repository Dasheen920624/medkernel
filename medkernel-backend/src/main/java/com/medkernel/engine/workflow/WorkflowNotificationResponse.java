package com.medkernel.engine.workflow;

import java.time.Instant;

/**
 * 统一通知响应 DTO。
 */
public record WorkflowNotificationResponse(
    String notificationId,
    String orgUnitId,
    WorkflowNotificationSourceType sourceType,
    String sourceId,
    String dedupeKey,
    String title,
    String message,
    WorkflowNotificationLevel level,
    WorkflowNotificationStatus status,
    String recipientId,
    String recipientRole,
    String patientId,
    String encounterId,
    String deepLink,
    Instant readAt,
    String readBy,
    String traceId
) {
    static WorkflowNotificationResponse from(WorkflowNotification notification) {
        return new WorkflowNotificationResponse(
            notification.notificationId(),
            notification.orgUnitId(),
            notification.sourceType(),
            notification.sourceId(),
            notification.dedupeKey(),
            notification.title(),
            notification.message(),
            notification.level(),
            notification.status(),
            notification.recipientId(),
            notification.recipientRole(),
            notification.patientId(),
            notification.encounterId(),
            notification.deepLink(),
            notification.readAt(),
            notification.readBy(),
            notification.traceId());
    }
}
