package com.medkernel.shared.config;

import java.time.Instant;

/**
 * 配置中心受控配置项。
 */
public record SystemConfigItem(
    String tenantId,
    String key,
    String value,
    String valueType,
    String displayName,
    String risk,
    String owner,
    String description,
    String source,
    boolean protectedConfig,
    boolean active,
    long version,
    Instant updatedAt
) {
}
