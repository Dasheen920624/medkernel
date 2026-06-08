package com.medkernel.engine.authoring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 统一资产库克隆请求。
 */
public record AuthoringAssetCloneRequest(
    @NotBlank(message = "新资产编码不能为空")
    String newCode,
    @NotBlank(message = "新资产名称不能为空")
    String newName,
    @NotNull(message = "新资产版本号不能为空")
    @Positive(message = "新资产版本号必须大于 0")
    Integer newVersion,
    @NotBlank(message = "新资产包版本不能为空")
    String packageVersion
) {}
