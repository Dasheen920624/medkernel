package com.medkernel.engine.workflow;

import java.util.Set;

import jakarta.validation.constraints.Pattern;

/**
 * 通知偏好设置请求。
 */
public record WorkflowNotificationSettingsRequest(
    Boolean inAppEnabled,
    Boolean smsEnabled,
    Boolean emailEnabled,
    Boolean pushEnabled,
    Boolean webhookEnabled,
    Boolean inHospitalMessageEnabled,
    Boolean quietHoursEnabled,

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "免打扰开始时间格式必须为 HH:mm")
    String quietStart,

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "免打扰结束时间格式必须为 HH:mm")
    String quietEnd,

    Set<WorkflowNotificationLevel> quietBypassLevels
) {
}
