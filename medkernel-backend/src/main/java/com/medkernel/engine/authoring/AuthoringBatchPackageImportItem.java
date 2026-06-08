package com.medkernel.engine.authoring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 一条配置包离线导入载荷。
 */
public record AuthoringBatchPackageImportItem(
    @NotBlank String itemId,
    @NotBlank @Size(max = 5_000_000) String offlinePackageJson
) {}
