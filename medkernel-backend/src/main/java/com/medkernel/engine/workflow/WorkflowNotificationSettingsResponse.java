package com.medkernel.engine.workflow;

import java.time.Instant;
import java.util.Set;

/**
 * 通知偏好设置响应。
 */
public record WorkflowNotificationSettingsResponse(
    boolean inAppEnabled,
    boolean smsEnabled,
    boolean emailEnabled,
    boolean pushEnabled,
    boolean quietHoursEnabled,
    String quietStart,
    String quietEnd,
    Set<WorkflowNotificationLevel> quietBypassLevels,
    boolean quietActiveNow,
    long version,
    Instant updatedAt,
    String updatedBy
) {
}
