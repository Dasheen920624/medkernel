package com.medkernel.engine.quality.insurance;

import jakarta.validation.constraints.NotBlank;

/**
 * DRG/DIP 入组核对请求。
 */
public record DrgGroupingRequest(
    @NotBlank String contextSnapshotId,
    @NotBlank String grouperVersion,
    @NotBlank String expectedGroupCode,
    @NotBlank String actualGroupCode,
    @NotBlank String responsibleDepartmentId,
    @NotBlank String explanation
) {}
