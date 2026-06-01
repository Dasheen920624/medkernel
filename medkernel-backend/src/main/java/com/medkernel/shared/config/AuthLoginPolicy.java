package com.medkernel.shared.config;

/**
 * 认证失败锁定与登录限流策略，运行时由配置中心读取。
 */
public record AuthLoginPolicy(
    int maxFailedAttempts,
    long lockoutSeconds,
    int rateLimitAttempts,
    long rateLimitWindowSeconds
) {
}
