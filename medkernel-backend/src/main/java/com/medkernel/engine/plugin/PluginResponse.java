package com.medkernel.engine.plugin;

import java.time.Instant;
import java.util.List;

/**
 * 插件响应。
 *
 * @param pluginId 插件实例 ID
 * @param pluginCode 插件编码
 * @param displayName 展示名称
 * @param status 生命周期状态
 * @param authorityBoundary 权限边界
 * @param capabilities 能力清单
 * @param version 版本号
 * @param updatedAt 更新时间
 */
public record PluginResponse(
    String pluginId,
    String pluginCode,
    String displayName,
    PluginStatus status,
    PluginAuthorityBoundary authorityBoundary,
    List<PluginCapabilityResponse> capabilities,
    long version,
    Instant updatedAt
) {
    public PluginResponse {
        capabilities = List.copyOf(capabilities);
    }
}
