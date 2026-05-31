package com.medkernel.shared.config;

import java.time.Instant;

/**
 * 配置中心历史变更记录。
 */
public record SystemConfigHistoryEntry(
    String tenantId,
    String key,
    String beforeValue,
    String afterValue,
    String changeType,
    long version,
    Instant createdAt,
    String createdBy
) {
}
