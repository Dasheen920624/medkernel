package com.medkernel.engine.workflow;

import java.time.Instant;
import java.util.List;

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
    String traceId,
    List<WorkflowNotificationDeliveryResponse> externalDeliveries
) {
    static WorkflowNotificationResponse from(WorkflowNotification notification) {
        return from(notification, List.of());
    }

    static WorkflowNotificationResponse from(
            WorkflowNotification notification,
            List<WorkflowNotificationDeliveryResponse> externalDeliveries) {
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
            notification.traceId(),
            externalDeliveries == null ? List.of() : List.copyOf(externalDeliveries));
    }
}
