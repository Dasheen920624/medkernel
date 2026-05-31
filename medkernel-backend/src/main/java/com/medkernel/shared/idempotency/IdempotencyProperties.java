package com.medkernel.shared.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * BASE-03 平台级幂等配置。
 *
 * @param enabled 是否启用 Idempotency-Key 过滤器
 * @param ttl     幂等结果保留窗口
 */
@Component
@ConfigurationProperties(prefix = "medkernel.api.idempotency")
public record IdempotencyProperties(
    boolean enabled,
    Duration ttl
) {

    public IdempotencyProperties {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofHours(24);
        }
    }

    public IdempotencyProperties() {
        this(true, Duration.ofHours(24));
    }
}
