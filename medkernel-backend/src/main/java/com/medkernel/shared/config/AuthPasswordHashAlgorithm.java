package com.medkernel.shared.config;

import java.util.Locale;

/**
 * 平台账号口令哈希算法：默认 BCrypt，国产化形态可切换到带盐 SM3。
 */
public enum AuthPasswordHashAlgorithm {
    BCRYPT,
    SM3;

    public static AuthPasswordHashAlgorithm parse(String value) {
        if (value == null || value.isBlank()) {
            return BCRYPT;
        }
        try {
            return AuthPasswordHashAlgorithm.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BCRYPT;
        }
    }
}
