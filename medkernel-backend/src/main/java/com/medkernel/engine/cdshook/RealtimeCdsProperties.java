package com.medkernel.engine.cdshook;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import com.medkernel.shared.config.RealtimeCdsSettings;

/**
 * P13-5 实时 CDS Hook 同步求值预算。
 */
@ConfigurationProperties(prefix = "medkernel.cdss.realtime")
public record RealtimeCdsProperties(
    Duration defaultTimeout,
    Duration orderSignTimeout
) implements RealtimeCdsSettings {

    @ConstructorBinding
    public RealtimeCdsProperties {
        if (defaultTimeout == null || defaultTimeout.isZero() || defaultTimeout.isNegative()) {
            defaultTimeout = Duration.ofSeconds(2);
        }
        if (orderSignTimeout == null || orderSignTimeout.isZero() || orderSignTimeout.isNegative()) {
            orderSignTimeout = Duration.ofSeconds(1);
        }
    }

    public RealtimeCdsProperties() {
        this(Duration.ofSeconds(2), Duration.ofSeconds(1));
    }
}
