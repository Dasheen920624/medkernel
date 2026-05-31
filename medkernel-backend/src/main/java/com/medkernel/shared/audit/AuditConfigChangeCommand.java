package com.medkernel.shared.audit;

import java.util.Objects;

/**
 * 审计相关配置变更命令。
 *
 * <p>配置中心或系统配置页面在保存审计相关配置前调用 {@link AuditSafetyGuard}；
 * BASE-04 先提供护栏端口，CONFIG-01 / D5 安全基线页复用该端口。
 *
 * @param key         配置键
 * @param beforeValue 变更前值，可为空
 * @param afterValue  变更后值，可为空
 * @param reason      变更原因，可为空
 */
public record AuditConfigChangeCommand(
    String key,
    String beforeValue,
    String afterValue,
    String reason
) {

    public AuditConfigChangeCommand {
        Objects.requireNonNull(key, "配置键不能为空");
        if (key.isBlank()) {
            throw new IllegalArgumentException("配置键不能为空");
        }
        key = key.trim();
        beforeValue = trimToNull(beforeValue);
        afterValue = trimToNull(afterValue);
        reason = trimToNull(reason);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
