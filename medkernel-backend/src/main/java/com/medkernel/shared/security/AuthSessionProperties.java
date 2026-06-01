package com.medkernel.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录会话安全策略配置。
 *
 * @param idleTimeoutSeconds 无操作自动登出窗口，单位秒
 * @param warningSeconds     无操作超时前提醒窗口，单位秒
 * @param maxDurationSeconds 单次登录最大会话时长，单位秒
 */
@ConfigurationProperties(prefix = "medkernel.auth.session")
public record AuthSessionProperties(
    long idleTimeoutSeconds,
    long warningSeconds,
    long maxDurationSeconds
) {
    public AuthSessionProperties {
        if (idleTimeoutSeconds <= 0) {
            idleTimeoutSeconds = 1800;
        }
        if (warningSeconds <= 0 || warningSeconds >= idleTimeoutSeconds) {
            warningSeconds = Math.min(120, Math.max(1, idleTimeoutSeconds / 4));
        }
        if (maxDurationSeconds <= 0 || maxDurationSeconds < idleTimeoutSeconds) {
            maxDurationSeconds = Math.max(28800, idleTimeoutSeconds);
        }
    }
}
