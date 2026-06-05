package com.medkernel.engine.emrlevel;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 电子病历评级目标保存请求。
 */
public record EmrLevelTargetUpsertRequest(
    @NotBlank String hospitalOrgId,
    @NotNull @Min(4) @Max(6) Integer targetLevel,
    @NotBlank String standardVersion,
    @NotNull @Size(min = 1) List<@Valid EmrLevelItemAssessmentRequest> items
) {
}
