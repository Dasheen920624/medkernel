package com.medkernel.shared.config;

import java.time.Instant;

/**
 * 配置中心对外响应。
 */
public record SystemConfigItemResponse(
    String key,
    String value,
    String valueType,
    String displayName,
    String risk,
    String owner,
    String description,
    String source,
    boolean protectedConfig,
    long version,
    Instant updatedAt
) {

    static SystemConfigItemResponse from(SystemConfigItem item) {
        return new SystemConfigItemResponse(
            item.key(),
            item.value(),
            item.valueType(),
            item.displayName(),
            item.risk(),
            item.owner(),
            item.description(),
            item.source(),
            item.protectedConfig(),
            item.version(),
            item.updatedAt());
    }
}
