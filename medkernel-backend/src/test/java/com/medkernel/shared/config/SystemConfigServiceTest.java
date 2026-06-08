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
    void runtimeFeatureFlagEnabledForTenantUsesTenantOverrideBeforeSystemFallback() {
        RuntimeProperties properties = new RuntimeProperties();
        RuntimeProperties.FeatureFlag clinicalOperatorFlag = new RuntimeProperties.FeatureFlag();
        clinicalOperatorFlag.setDisplayName("临床算子");
        clinicalOperatorFlag.setEnabled(true);
        clinicalOperatorFlag.setRisk("HIGH");
        clinicalOperatorFlag.setOwner("医务处 / 信息科");
        clinicalOperatorFlag.setDescription("控制规则与路径求值是否启用临床算子。");
        properties.setFeatureFlags(Map.of("authoring-clinical-operators", clinicalOperatorFlag));
        String key = "medkernel.runtime.feature-flags.authoring-clinical-operators.enabled";
        when(repository.findActive(SystemConfigService.SYSTEM_TENANT, key))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                key,
                "true",
                "BOOLEAN",
                "临床算子",
                "HIGH",
                "医务处 / 信息科",
                "控制规则与路径求值是否启用临床算子。",
                "YML_SEED",
                true,
                true,
                1,
                null)));
        when(repository.findActive("tenant-A", key))
            .thenReturn(Optional.of(new SystemConfigItem(
                "tenant-A",
                key,
                "false",
                "BOOLEAN",
                "临床算子",
                "HIGH",
                "医务处 / 信息科",
                "控制规则与路径求值是否启用临床算子。",
                "API",
                true,
                true,
                2,
                null)));
        when(repository.findActive("tenant-B", key)).thenReturn(Optional.empty());

        assertThat(service.runtimeFeatureFlagEnabledForTenant(
            properties,
            "authoring-clinical-operators",
            "tenant-A")).isFalse();
        assertThat(service.runtimeFeatureFlagEnabledForTenant(
            properties,
            "authoring-clinical-operators",
            "tenant-B")).isTrue();
    }

    @Test
    void tenantConfigListUsesTenantOverrideAndMarksInheritedSystemItems() {
        String clinicalKey = "medkernel.runtime.feature-flags.authoring-clinical-operators.enabled";
        String graphKey = "medkernel.runtime.feature-flags.graph-projection.enabled";
        SystemConfigItem systemClinical = configItem(
            SystemConfigService.SYSTEM_TENANT,
            clinicalKey,
            "true",
            "临床算子",
            "YML_SEED",
            1);
        SystemConfigItem systemGraph = configItem(
            SystemConfigService.SYSTEM_TENANT,
            graphKey,
            "false",
            "知识图谱投影",
            "YML_SEED",
            1);
        SystemConfigItem tenantClinical = configItem(
            "tenant-A",
            clinicalKey,
            "false",
            "临床算子",
            "API",
            2);
        when(repository.listActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.RUNTIME_FLAG_PREFIX))
            .thenReturn(java.util.List.of(systemClinical, systemGraph));
        when(repository.listActive("tenant-A", SystemConfigService.RUNTIME_FLAG_PREFIX))
            .thenReturn(java.util.List.of(tenantClinical));

        java.util.List<SystemConfigItemResponse> configs =
            service.listTenantMerged("tenant-A", SystemConfigService.RUNTIME_FLAG_PREFIX);

        assertThat(configs).extracting(SystemConfigItemResponse::key)
            .containsExactly(clinicalKey, graphKey);
        assertThat(configs.get(0).value()).isEqualTo("false");
        assertThat(configs.get(0).source()).isEqualTo("API");
        assertThat(configs.get(1).value()).isEqualTo("false");
        assertThat(configs.get(1).source()).isEqualTo("SYSTEM_INHERITED");
    }

    @Test
    void tenantOverrideUpdateSeedsMissingTenantConfigBeforeAuditedUpdate() {
        String key = "medkernel.runtime.feature-flags.authoring-clinical-operators.enabled";
        SystemConfigItem systemItem =
            configItem(SystemConfigService.SYSTEM_TENANT, key, "true", "临床算子", "YML_SEED", 1);
        SystemConfigItem seededTenant =
            configItem("tenant-A", key, "true", "临床算子", "SYSTEM_INHERITED", 1);
        SystemConfigItem updatedTenant =
            configItem("tenant-A", key, "false", "临床算子", "API", 2);
        when(repository.findActive("tenant-A", key))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(seededTenant));
        when(repository.findActive(SystemConfigService.SYSTEM_TENANT, key)).thenReturn(Optional.of(systemItem));
        when(repository.updateValue(
            "tenant-A",
            key,
            "false",
            "admin-1",
            "租户灰度回退",
            null))
            .thenReturn(updatedTenant);

        SystemConfigItemResponse response = service.updateTenantOverride(
            "tenant-A",
            key,
            new SystemConfigUpdateRequest("false", "租户灰度回退", 99L, true),
            "admin-1");

        assertThat(response.value()).isEqualTo("false");
        assertThat(response.version()).isEqualTo(2);
        verify(repository).insertSeedIfAbsent(any(SystemConfigSeed.class), eq("admin-1"));
        verify(repository).updateValue("tenant-A", key, "false", "admin-1", "租户灰度回退", null);
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

    private static SystemConfigItem configItem(
            String tenantId,
            String key,
            String value,
            String displayName,
            String source,
            long version) {
        return new SystemConfigItem(
            tenantId,
            key,
            value,
            "BOOLEAN",
            displayName,
            "HIGH",
            "医务处 / 信息科",
            displayName + "运行开关。",
            source,
            true,
            true,
            version,
            null);
    }
}
