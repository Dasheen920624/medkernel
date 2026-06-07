package com.medkernel.engine.plugin;

import java.util.List;

/**
 * 插件授权响应。
 *
 * @param pluginId 插件实例 ID
 * @param status 授权结果
 * @param grants 授权项
 */
public record PluginGrantResponse(
    String pluginId,
    PluginGrantStatus status,
    List<PluginGrantItemResponse> grants
) {
    public PluginGrantResponse {
        grants = List.copyOf(grants);
    }
}
