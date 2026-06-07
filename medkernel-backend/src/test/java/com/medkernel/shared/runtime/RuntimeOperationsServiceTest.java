package com.medkernel.shared.runtime;

import java.time.Instant;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.core.env.Environment;

import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupReadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeOperationsServiceTest {

    @Test
    void successfulRestoreDrillPromotesBackupReadinessToUp() {
        Environment environment = mock(Environment.class);
        HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
        SystemConfigService configService = mock(SystemConfigService.class);
        RuntimeBackupDrillEvidenceReader evidenceReader = mock(RuntimeBackupDrillEvidenceReader.class);
        RuntimeProperties properties = new RuntimeProperties();
        properties.getBackup().setEnabled(true);

        when(environment.getProperty("spring.application.name", "medkernel")).thenReturn("medkernel");
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        when(environment.getProperty("spring.threads.virtual.enabled", Boolean.class, false))
            .thenReturn(false);
        when(healthEndpoint.health()).thenReturn(Health.up().build());
        when(configService.runtimeFeatureFlags(properties)).thenReturn(java.util.List.of());
        when(configService.runtimeFeatureFlagEnabled(any(), any())).thenReturn(false);
        when(configService.runtimeBackupReadiness(properties)).thenReturn(new RuntimeBackupReadiness(
            true,
            "24 小时",
            "4 小时",
            "./backup.sh",
            "./restore.sh",
            "SHA-256",
            RuntimeBackupDrillEvidence.notAvailable(),
            "YML_SEED",
            null
        ));
        when(evidenceReader.read(any())).thenReturn(new RuntimeBackupDrillEvidence(
            "SUCCESS",
            Instant.parse("2026-06-06T16:30:00Z"),
            96,
            "latest-restore-drill.properties",
            "隔离恢复演练通过，迁移历史校验正常"
        ));

        RuntimeOperationsSnapshot snapshot = new RuntimeOperationsService(
            environment,
            healthEndpoint,
            new SimpleMeterRegistry(),
            properties,
            configService,
            evidenceReader
        ).snapshot();

        RuntimeOperationsSnapshot.RuntimeDependencyStatus backup = snapshot.dependencies().stream()
            .filter(item -> item.key().equals("backup-restore"))
            .findFirst()
            .orElseThrow();
        assertThat(backup.status()).isEqualTo("UP");
        assertThat(backup.detail()).contains("隔离恢复演练通过");
    }
}
