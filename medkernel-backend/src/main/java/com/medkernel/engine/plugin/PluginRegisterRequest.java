package com.medkernel.engine.plugin;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 插件注册请求。
 *
 * @param pluginCode 租户内插件编码
 * @param displayName 展示名称
 * @param capabilities 能力声明
 */
public record PluginRegisterRequest(
    @NotBlank @Size(max = 128) String pluginCode,
    @NotBlank @Size(max = 128) String displayName,
    @NotEmpty List<@Valid PluginCapabilityRequest> capabilities
) {
}
