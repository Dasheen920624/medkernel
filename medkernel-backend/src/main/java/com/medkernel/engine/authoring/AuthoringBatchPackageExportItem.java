package com.medkernel.engine.authoring;

import jakarta.validation.constraints.NotBlank;

/**
 * 一条配置包离线导出目标。
 */
public record AuthoringBatchPackageExportItem(
    @NotBlank String itemId,
    @NotBlank String packageId,
    @NotBlank String targetOrgUnitId
) {}
