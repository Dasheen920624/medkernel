package com.medkernel.engine.pkg;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 配置包离线导入请求 DTO。
 */
public record PackageOfflineImportRequest(
    @NotBlank(message = "离线包 JSON 不能为空")
    @Size(max = 5_000_000, message = "离线包 JSON 不能超过 5MB")
    String offlinePackageJson
) {}
