package com.medkernel.engine.quality.insurance;

import jakarta.validation.constraints.NotBlank;

/**
 * 触发病案内涵质控的请求。
 */
public record QualityCaseReviewRequest(
    @NotBlank String contextSnapshotId,
    @NotBlank String scenarioCode,
    String packageVersion,
    @NotBlank String responsibleDepartmentId
) {}
