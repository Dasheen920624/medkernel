package com.medkernel.shared.security;

import java.util.Locale;

/**
 * 认证运行模式，由配置中心 {@code medkernel.auth.mode} 管理。
 */
public enum AuthMode {
    PLATFORM,
    DELEGATED,
    BOTH;

    public boolean allowsPlatformLogin() {
        return this == PLATFORM || this == BOTH;
    }

    public boolean allowsDelegatedLogin() {
        return this == DELEGATED || this == BOTH;
    }

    public static AuthMode parse(String value, AuthMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return AuthMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
