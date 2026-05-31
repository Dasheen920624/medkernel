package com.medkernel.shared.config;

import java.time.Instant;

/**
 * 启动期从受控配置文件导入的配置种子。
 */
public record SystemConfigSeed(
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
    Instant seededAt
) {
}
