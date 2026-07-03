package com.medkernel.shared.runtime;

import java.time.Instant;
import java.util.List;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeOperationsServiceTest {

    @Test
    void successfulRestoreDrillPromotesBackupReadinessToUp() {
        RuntimeProperties properties = new RuntimeProperties();
        properties.getBackup().setEnabled(true);
        RuntimeOperationsFixture fixture = fixture(properties);
        when(fixture.evidenceReader().read(any())).thenReturn(new RuntimeBackupDrillEvidence(
            "SUCCESS",
            Instant.parse("2026-06-06T16:30:00Z"),
            96,
            "latest-restore-drill.properties",
            "隔离恢复演练通过，迁移历史校验正常"
        ));

        RuntimeOperationsSnapshot snapshot = fixture.service().snapshot();

        RuntimeOperationsSnapshot.RuntimeDependencyStatus backup = snapshot.dependencies().stream()
            .filter(item -> item.key().equals("backup-restore"))
            .findFirst()
            .orElseThrow();
        assertThat(backup.status()).isEqualTo("UP");
        assertThat(backup.detail()).contains("隔离恢复演练通过");
    }

    @Test
    void domesticCompatibilityWarnsInsteadOfFalseGreenForNonDomesticRuntime() {
        RuntimeProperties properties = new RuntimeProperties();
        properties.setDatabaseDialect("postgres");
        properties.setMigrationLocation("classpath:db/migration/postgres");

        RuntimeOperationsSnapshot snapshot = fixture(properties).service().snapshot();

        assertThat(snapshot.domesticCompatibility().overallStatus()).isEqualTo("WARN");
        assertThat(snapshot.domesticCompatibility().items())
            .extracting(RuntimeOperationsSnapshot.RuntimeDomesticCheckItem::key)
            .containsExactly(
                "os",
                "jdk",
                "database",
                "crypto-provider",
                "middleware",
                "browser",
                "ca"
            );
        RuntimeOperationsSnapshot.RuntimeDomesticCheckItem database = snapshot.domesticCompatibility().items().stream()
            .filter(item -> item.key().equals("database"))
            .findFirst()
            .orElseThrow();
        assertThat(database.status()).isEqualTo("WARN");
        assertThat(database.actualValue()).contains("postgres");
        assertThat(database.reason()).contains("不标记通过");
        RuntimeOperationsSnapshot.RuntimeDomesticCheckItem browser = snapshot.domesticCompatibility().items().stream()
            .filter(item -> item.key().equals("browser"))
            .findFirst()
            .orElseThrow();
        assertThat(browser.status()).isEqualTo("UNKNOWN");
        assertThat(browser.reason()).contains("服务端无法读取客户端浏览器");
    }

    @Test
    void domesticReportUsesSameRuntimeSnapshotAndKeepsWarningsVisible() {
        RuntimeProperties properties = new RuntimeProperties();
        properties.setDatabaseDialect("postgres");
        properties.setMigrationLocation("classpath:db/migration/postgres");

        String report = fixture(properties).service().domesticReport();

        assertThat(report)
            .contains("MedKernel 国产化适配自检报告")
            .contains("整体状态: WARN")
            .contains("关系数据库")
            .contains("postgres")
            .contains("不标记通过")
            .doesNotContain("password")
            .doesNotContain("secret");
    }

    @Test
    void enabledOptionalDependenciesUseConfiguredButUnverifiedLanguage() {
        RuntimeProperties properties = new RuntimeProperties();
        RuntimeOperationsFixture fixture = fixture(properties);
        when(fixture.configService().runtimeFeatureFlagEnabled(any(), eq("graph-projection"))).thenReturn(true);
        when(fixture.configService().runtimeFeatureFlagEnabled(any(), eq("search-projection"))).thenReturn(true);
        when(fixture.configService().runtimeFeatureFlagEnabled(any(), eq("dify-workflow"))).thenReturn(true);
        when(fixture.configService().runtimeFeatureFlagEnabled(any(), eq("external-provider"))).thenReturn(true);

        RuntimeOperationsSnapshot snapshot = fixture.service().snapshot();

        assertThat(snapshot.dependencies())
            .filteredOn(item -> List.of(
                "graph-projection",
                "search-projection",
                "dify-workflow",
                "model-gateway",
                "external-provider"
            ).contains(item.key()))
            .allSatisfy(item -> assertThat(item.detail())
                .doesNotContain("未接入")
                .doesNotContain("暂不判定通过"));
        assertThat(dependencyDetail(snapshot, "graph-projection")).contains("连接健康验证");
        assertThat(dependencyDetail(snapshot, "model-gateway")).contains("模型能力页");
    }

    private RuntimeOperationsFixture fixture(RuntimeProperties properties) {
        Environment environment = mock(Environment.class);
        HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
        SystemConfigService configService = mock(SystemConfigService.class);
        RuntimeBackupDrillEvidenceReader evidenceReader = mock(RuntimeBackupDrillEvidenceReader.class);

        when(environment.getProperty("spring.application.name", "medkernel")).thenReturn("medkernel");
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        when(environment.getProperty("spring.threads.virtual.enabled", Boolean.class, false))
            .thenReturn(false);
        when(healthEndpoint.health()).thenReturn(Health.up().build());
        when(configService.runtimeFeatureFlags(properties)).thenReturn(List.of());
        when(configService.runtimeFeatureFlagEnabled(any(), any())).thenReturn(false);
        when(configService.runtimeBackupReadiness(properties)).thenReturn(new RuntimeBackupReadiness(
            properties.getBackup().isEnabled(),
            "24 小时",
            "4 小时",
            "./backup.sh",
            "./restore.sh",
            "SHA-256",
            RuntimeBackupDrillEvidence.notAvailable(),
            "YML_SEED",
            null
        ));
        when(evidenceReader.read(any())).thenReturn(RuntimeBackupDrillEvidence.notAvailable());

        return new RuntimeOperationsFixture(evidenceReader, configService, new RuntimeOperationsService(
            environment,
            healthEndpoint,
            new SimpleMeterRegistry(),
            properties,
            configService,
            evidenceReader
        ));
    }

    private String dependencyDetail(RuntimeOperationsSnapshot snapshot, String key) {
        return snapshot.dependencies().stream()
            .filter(item -> item.key().equals(key))
            .findFirst()
            .orElseThrow()
            .detail();
    }

    private record RuntimeOperationsFixture(
        RuntimeBackupDrillEvidenceReader evidenceReader,
        SystemConfigService configService,
        RuntimeOperationsService service
    ) {
    }
}
