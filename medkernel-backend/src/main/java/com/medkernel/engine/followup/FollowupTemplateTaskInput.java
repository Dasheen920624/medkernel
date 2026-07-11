package com.medkernel.engine.followup;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 随访方案任务定义。
 */
public record FollowupTemplateTaskInput(
    @NotNull FollowupTaskType taskType,
    @NotNull @Min(0) @Max(3650) Integer delayDays,
    @Size(max = 128) String questionnaireTemplateId
) {
}
