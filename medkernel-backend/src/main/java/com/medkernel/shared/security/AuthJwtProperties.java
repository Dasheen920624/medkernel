package com.medkernel.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 启动兜底配置；运行时有效期以配置中心为准。
 */
@ConfigurationProperties(prefix = "medkernel.auth.jwt")
public record AuthJwtProperties(
    long ttlSeconds
) {
    public AuthJwtProperties {
        if (ttlSeconds <= 0) {
            ttlSeconds = 28800;
        }
    }
}
