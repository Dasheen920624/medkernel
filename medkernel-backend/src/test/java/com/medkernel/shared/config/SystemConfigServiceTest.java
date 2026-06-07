package com.medkernel.shared.config;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.AuditSafetyGuard;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupReadiness;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeFeatureFlag;
import com.medkernel.shared.runtime.RuntimeProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置中心服务层安全兜底测试。
 */
class SystemConfigServiceTest {

    private static final String GRAPH_CONFIG_KEY = "medkernel.runtime.feature-flags.graph-projection.enabled";

    private final SystemConfigRepository repository = mock(SystemConfigRepository.class);
    private final AuditSafetyGuard auditSafetyGuard = mock(AuditSafetyGuard.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final RuntimeLogLevelManager logLevelManager = mock(RuntimeLogLevelManager.class);
    private final HighRiskChangeGuard highRiskChangeGuard = mock(HighRiskChangeGuard.class);
    private final SystemConfigService service = new SystemConfigService(
        repository, auditSafetyGuard, auditRecorder, logLevelManager, highRiskChangeGuard);

    @Test
    void tenantConfigurationIsSeededAndReadWithinItsOwnTenantBoundary() {
        String key = "medkernel.notification.defaults";
        Instant now = Instant.parse("2026-06-07T02:00:00Z");
        SystemConfigSeed seed = new SystemConfigSeed(
            "tenant-A",
            key,
            "{}",
            "JSON",
            "租户通知默认策略",
            "MEDIUM",
            "医院管理员",
            "租户通知默认策略。",
            "SAFE_DEFAULT",
            false,
            now);
        SystemConfigItem saved = new SystemConfigItem(
            "tenant-A",
            key,
            "{}",
            "JSON",
            "租户通知默认策略",
            "MEDIUM",
            "医院管理员",
            "租户通知默认策略。",
            "SAFE_DEFAULT",
            false,
            true,
            1,
            now);
        when(repository.findActive("tenant-A", key))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(saved));

        SystemConfigItemResponse response =
            service.getOrSeedTenantConfig("tenant-A", key, seed, "admin-1");

        assertThat(response.key()).isEqualTo(key);
        assertThat(response.version()).isEqualTo(1);
        verify(repository).insertSeedIfAbsent(any(SystemConfigSeed.class), eq("admin-1"));
    }

    @Test
    void tenantConfigurationUpdateUsesTenantScopedOptimisticVersion() {
        String key = "medkernel.notification.defaults";
        SystemConfigItem before = new SystemConfigItem(
            "tenant-A",
            key,
            "{}",
            "JSON",
            "租户通知默认策略",
            "MEDIUM",
            "医院管理员",
            "租户通知默认策略。",
            "SAFE_DEFAULT",
            false,
            true,
            3,
            null);
        SystemConfigItem after = new SystemConfigItem(
            "tenant-A",
            key,
            "{\"inAppEnabled\":true}",
            "JSON",
            "租户通知默认策略",
            "MEDIUM",
            "医院管理员",
            "租户通知默认策略。",
            "API",
            false,
            true,
            4,
            null);
        when(repository.findActive("tenant-A", key)).thenReturn(Optional.of(before));
        when(repository.updateValue(
                "tenant-A",
                key,
                "{\"inAppEnabled\":true}",
                "admin-1",
                "更新医院默认策略",
                3L))
            .thenReturn(after);

        SystemConfigItemResponse response = service.updateTenant(
            "tenant-A",
            key,
            new SystemConfigUpdateRequest(
                "{\"inAppEnabled\":true}",
                "更新医院默认策略",
                3L,
                false),
            "admin-1");

        assertThat(response.version()).isEqualTo(4);
        verify(repository).updateValue(
            "tenant-A",
            key,
            "{\"inAppEnabled\":true}",
            "admin-1",
            "更新医院默认策略",
            3L);
        verify(auditRecorder).record(any());
    }

    @Test
    void runtimeFeatureFlagsFallBackToSafeDefaultWithHonestWarningWhenConfigStoreReadFails() {
        RuntimeProperties properties = new RuntimeProperties();
        RuntimeProperties.FeatureFlag graphFlag = new RuntimeProperties.FeatureFlag();
        graphFlag.setDisplayName("知识图谱投影");
        graphFlag.setEnabled(false);
        graphFlag.setRisk("MEDIUM");
        graphFlag.setOwner("信息科 / 架构组");
        graphFlag.setDescription("控制 Neo4j 图谱投影和图谱查询能力是否参与运行。");
        properties.setFeatureFlags(Map.of("graph-projection", graphFlag));

        when(repository.findActive(SystemConfigService.SYSTEM_TENANT, GRAPH_CONFIG_KEY))
            .thenThrow(new DataAccessResourceFailureException("config database unavailable"));

        RuntimeFeatureFlag flag = service.runtimeFeatureFlags(properties).get(0);

        assertThat(flag.enabled()).isFalse();
        assertThat(flag.source()).isEqualTo("SAFE_DEFAULT");
        assertThat(flag.warning()).contains("配置中心读取失败").contains("安全默认");
        assertThat(service.runtimeFeatureFlagEnabled(properties, "graph-projection")).isFalse();
    }

    @Test
    void runtimeFeatureFlagsFallBackToSafeDefaultWithHonestWarningWhenBooleanValueIsInvalid() {
        RuntimeProperties properties = new RuntimeProperties();
        RuntimeProperties.FeatureFlag graphFlag = new RuntimeProperties.FeatureFlag();
        graphFlag.setDisplayName("知识图谱投影");
        graphFlag.setEnabled(false);
        graphFlag.setRisk("MEDIUM");
        graphFlag.setOwner("信息科 / 架构组");
        graphFlag.setDescription("控制 Neo4j 图谱投影和图谱查询能力是否参与运行。");
        properties.setFeatureFlags(Map.of("graph-projection", graphFlag));
        when(repository.findActive(SystemConfigService.SYSTEM_TENANT, GRAPH_CONFIG_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                GRAPH_CONFIG_KEY,
                "not-a-boolean",
                "BOOLEAN",
                "知识图谱投影",
                "MEDIUM",
                "信息科 / 架构组",
                "控制 Neo4j 图谱投影和图谱查询能力是否参与运行。",
                "API",
                false,
                true,
                7,
                null)));

        RuntimeFeatureFlag flag = service.runtimeFeatureFlags(properties).get(0);

        assertThat(flag.enabled()).isFalse();
        assertThat(flag.source()).isEqualTo("SAFE_DEFAULT");
        assertThat(flag.warning()).contains("配置中心布尔值非法").contains("安全默认");
        assertThat(service.runtimeFeatureFlagEnabled(properties, "graph-projection")).isFalse();
    }

    @Test
    void runtimeBackupReadinessFallsBackToSafeDefaultWithHonestWarningWhenConfigStoreReadFails() {
        RuntimeProperties properties = new RuntimeProperties();
        when(repository.findActive(eq(SystemConfigService.SYSTEM_TENANT), anyString()))
            .thenReturn(Optional.empty());
        when(repository.findActive(SystemConfigService.SYSTEM_TENANT, "medkernel.runtime.backup.enabled"))
            .thenThrow(new DataAccessResourceFailureException("config database unavailable"));

        RuntimeBackupReadiness backup = service.runtimeBackupReadiness(properties);

        assertThat(backup.enabled()).isFalse();
        assertThat(backup.rpo()).isEqualTo("未启用");
        assertThat(backup.source()).isEqualTo("SAFE_DEFAULT");
        assertThat(backup.warning()).contains("配置中心读取失败").contains("安全默认");
    }

    @Test
    void runtimeIntegrationHealthProbeIntervalReadsConfigCenterAndFallsBackSafely() {
        IntegrationHealthProbeSettings settings = () -> 300_000L;
        when(repository.findActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.INTEGRATION_HEALTH_PROBE_INTERVAL_MS_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                SystemConfigService.INTEGRATION_HEALTH_PROBE_INTERVAL_MS_KEY,
                "45000",
                "INTEGER",
                "第三方适配器周期探活间隔",
                "MEDIUM",
                "信息科 / 集成组",
                "控制第三方适配器周期健康探测间隔。",
                "API",
                false,
                true,
                3,
                null)));

        assertThat(service.runtimeIntegrationHealthProbeIntervalMs(settings)).isEqualTo(45_000L);

        when(repository.findActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.INTEGRATION_HEALTH_PROBE_INTERVAL_MS_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                SystemConfigService.INTEGRATION_HEALTH_PROBE_INTERVAL_MS_KEY,
                "not-a-number",
                "INTEGER",
                "第三方适配器周期探活间隔",
                "MEDIUM",
                "信息科 / 集成组",
                "控制第三方适配器周期健康探测间隔。",
                "API",
                false,
                true,
                4,
                null)));

        assertThat(service.runtimeIntegrationHealthProbeIntervalMs(settings)).isEqualTo(300_000L);
    }
}
