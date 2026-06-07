package com.medkernel.engine.plugin;

import java.util.List;

/**
 * 插件列表响应。
 *
 * @param items 当前租户插件
 */
public record PluginListResponse(
    List<PluginResponse> items
) {
    public PluginListResponse {
        items = List.copyOf(items);
    }
}
