package com.medkernel.engine.workflow;

import java.time.Instant;

/**
 * 随访通知事件投影为统一通知的只读行。
 */
public record FollowupNotificationRow(
    String eventId,
    String planId,
    String patientId,
    String encounterId,
    String taskId,
    String executorId,
    String executorType,
    String title,
    String message,
    String traceId,
    Instant createdAt
) {
}
