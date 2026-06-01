package com.medkernel.engine.experience;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 主题偏好写入请求。
 */
public record ThemePreferenceRequest(
    @NotBlank(message = "主题模式不能为空")
    @Pattern(regexp = "default|elder|dark|eye|system", message = "主题模式只允许 default、elder、dark、eye、system")
    String mode
) {
}
