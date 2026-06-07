package com.medkernel.engine.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 租户通知默认策略变更请求。
 */
public record WorkflowNotificationSystemSettingsRequest(
    @NotNull @Valid WorkflowNotificationSettingsRequest settings,
    @NotBlank @Size(max = 500) String reason,
    @NotNull @Positive Long expectedVersion
) {
}
