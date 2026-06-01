package com.medkernel.shared.config;

/**
 * 认证口令强度策略，运行时由配置中心读取。
 */
public record AuthPasswordPolicy(
    int minLength,
    boolean requireUppercase,
    boolean requireLowercase,
    boolean requireDigit,
    boolean requireSymbol
) {
}
