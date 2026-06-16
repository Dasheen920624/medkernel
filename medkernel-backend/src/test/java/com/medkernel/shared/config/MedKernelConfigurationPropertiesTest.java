package com.medkernel.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.medkernel.MedKernelConfigurationProperties;
import com.medkernel.engine.cdshook.RealtimeCdsProperties;
import com.medkernel.engine.context.ClinicalEventProperties;
import com.medkernel.engine.integration.service.IntegrationProperties;
import com.medkernel.shared.idempotency.IdempotencyProperties;

class MedKernelConfigurationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(MedKernelConfigurationProperties.class);

    @Test
    void bindsConstructorBasedRuntimePropertiesFromExternalConfig() {
        contextRunner
            .withPropertyValues(
                "medkernel.events.worker-enabled=false",
                "medkernel.events.worker-poll-interval-ms=1234",
                "medkernel.events.sync-timeout=7s",
                "medkernel.integration.health-probe-interval-ms=45000",
                "medkernel.cdss.realtime.default-timeout=5s",
                "medkernel.cdss.realtime.order-sign-timeout=1500ms",
                "medkernel.api.idempotency.enabled=false",
                "medkernel.api.idempotency.ttl=2h"
            )
            .run(context -> {
                ClinicalEventProperties events = context.getBean(ClinicalEventProperties.class);
                assertThat(events.workerEnabled()).isFalse();
                assertThat(events.workerPollIntervalMs()).isEqualTo(1234L);
                assertThat(events.syncTimeout()).isEqualTo(Duration.ofSeconds(7));

                IntegrationProperties integration = context.getBean(IntegrationProperties.class);
                assertThat(integration.healthProbeIntervalMs()).isEqualTo(45_000L);

                RealtimeCdsProperties realtimeCds = context.getBean(RealtimeCdsProperties.class);
                assertThat(realtimeCds.defaultTimeout()).isEqualTo(Duration.ofSeconds(5));
                assertThat(realtimeCds.orderSignTimeout()).isEqualTo(Duration.ofMillis(1500));

                IdempotencyProperties idempotency = context.getBean(IdempotencyProperties.class);
                assertThat(idempotency.enabled()).isFalse();
                assertThat(idempotency.ttl()).isEqualTo(Duration.ofHours(2));
            });
    }
}
