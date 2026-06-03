package com.medkernel.engine.integration.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.medkernel.shared.config.IntegrationHealthProbeSettings;

/**
 * 第三方对接总线运行配置。
 */
@Component
@ConfigurationProperties(prefix = "medkernel.integration")
public record IntegrationProperties(long healthProbeIntervalMs) implements IntegrationHealthProbeSettings {

    public IntegrationProperties {
        if (healthProbeIntervalMs <= 0) {
            healthProbeIntervalMs = 300_000L;
        }
    }

    public IntegrationProperties() {
        this(300_000L);
    }
}
