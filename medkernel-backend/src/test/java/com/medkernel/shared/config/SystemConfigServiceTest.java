package com.medkernel.shared.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.AuditSafetyGuard;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupReadiness;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeFeatureFlag;
import com.medkernel.shared.runtime.RuntimeProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private final IsolatedAuditPublisher isolatedAuditPublisher = mock(IsolatedAuditPublisher.class);
    private final RuntimeLogLevelManager logLevelManager = mock(RuntimeLogLevelManager.class);
    private final HighRiskChangeGuard highRiskChangeGuard = mock(HighRiskChangeGuard.class);
    private final PrivilegedConfigChangeGuard privilegedConfigChangeGuard = mock(PrivilegedConfigChangeGuard.class);
    private final SystemConfigSeedWriter seedWriter = new SystemConfigSeedWriter(repository);
    private final SystemConfigService service = new SystemConfigService(
        repository,
        auditSafetyGuard,
        auditRecorder,
        isolatedAuditPublisher,
        logLevelManager,
        highRiskChangeGuard,
        privilegedConfigChangeGuard,
        seedWriter);

    @Test
    void p6AcceptanceCannotBeEnabledByNonSystemSuperAdmin() {
        String key = SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY;
        SystemConfigItem before =
            configItem(SystemConfigService.SYSTEM_TENANT, key, "false", "P6 正式知识生产独立验收", "PLATFORM_SEED", 1);
        SystemConfigItem after =
            configItem(SystemConfigService.SYSTEM_TENANT, key, "true", "P6 正式知识生产独立验收", "API", 2);
        when(repository.findActive(SystemConfigService.SYSTEM_TENANT, key)).thenReturn(Optional.of(before));
        when(repository.updateValue(
            SystemConfigService.SYSTEM_TENANT,
            key,
            "true",
            "integration-operator",
            "完成 P6 独立验收",
            1L)).thenReturn(after);
        doThrow(ApiException.forbidden("当前账号缺少特权配置变更权限"))
            .when(privilegedConfigChangeGuard)
            .assertSystemSuperAdminAllowed("system_config", key);

        assertThatThrownBy(() -> service.update(
            key,
            new SystemConfigUpdateRequest("true", "完成 P6 独立验收", 1L, true),
            "integration-operator"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("内置超级管理员");

        verify(repository, never()).updateValue(
            anyString(), anyString(), anyString(), anyString(), anyString(), eq(1L));
        verify(isolatedAuditPublisher).publishInNewTx(argThat(event ->
            AuditEvent.OUTCOME_FAILED.equals(event.outcome())
                && "ENG-API-004".equals(event.errorCode())
                && AuditAction.PERMISSION_CHANGE.equals(event.action())
                && "system_config".equals(event.resourceType())
                && key.equals(event.resourceId())));
    }

    @Test
    void p6AcceptanceRejectsTenantOverrideBecauseRuntimeReadsOnlySystemFact() {
        String key = SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY;
        SystemConfigItem tenantItem =
            configItem("tenant-A", key, "false", "P6 正式知识生产独立验收", "SYSTEM_INHERITED", 1);
        SystemConfigItem updatedTenant =
            configItem("tenant-A", key, "true", "P6 正式知识生产独立验收", "API", 2);
        when(repository.findActive("tenant-A", key)).thenReturn(Optional.of(tenantItem));
        when(repository.updateValue(
            "tenant-A",
            key,
            "true",
            "tenant-admin",
            "租户尝试放行 P6",
            1L)).thenReturn(updatedTenant);

        assertThatThrownBy(() -> service.updateTenantOverride(
            "tenant-A",
            key,
            new SystemConfigUpdateRequest("true", "租户尝试放行 P6", 1L, true),
            "tenant-admin"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("仅允许系统级维护");

        verify(repository, never()).updateValue(
            anyString(), anyString(), anyString(), anyString(), anyString(), eq(1L));
    }

    @Test
    void p6AcceptanceRejectsTenantDefaultSeedingBecauseItIsASystemFact() {
        String key = SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY;
        SystemConfigSeed seed = new SystemConfigSeed(
            "tenant-A",
            key,
            "false",
            "BOOLEAN",
            "P6 正式知识生产独立验收",
            "HIGH",
            "平台知识治理组",
            "正式模型生成知识的独立验收放行标记。",
            "SAFE_DEFAULT",
            true,
            Instant.parse("2026-06-18T10:00:00Z"));

        assertThatThrownBy(() -> service.getOrSeedTenantConfig("tenant-A", key, seed, "tenant-admin"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("仅允许系统级维护");

        verify(repository, never()).insertSeedIfAbsent(any(SystemConfigSeed.class), anyString());
    }

    @Test
    void p6AcceptanceRejectsDirectTenantSeedBecauseItIsASystemFact() {
        String key = SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY;
        SystemConfigSeed seed = new SystemConfigSeed(
            "tenant-A",
            key,
            "false",
            "BOOLEAN",
            "P6 正式知识生产独立验收",
            "HIGH",
            "平台知识治理组",
            "正式模型生成知识的独立验收放行标记。",
            "SAFE_DEFAULT",
            true,
            Instant.parse("2026-06-18T10:00:00Z"));

        assertThatThrownBy(() -> service.seed(seed, "system"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("仅允许系统级维护");

        verify(repository, never()).insertSeedIfAbsent(any(SystemConfigSeed.class), anyString());
    }

    @Test
    void p6AcceptanceEnableUsesPrivilegedGuardBeforePersisting() {
        String key = SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY;
        SystemConfigItem before =
            configItem(SystemConfigService.SYSTEM_TENANT, key, "false", "P6 正式知识生产独立验收", "PLATFORM_SEED", 1);
        SystemConfigItem after =
            configItem(SystemConfigService.SYSTEM_TENANT, key, "true", "P6 正式知识生产独立验收", "API", 2);
        when(repository.findActive(SystemConfigService.SYSTEM_TENANT, key)).thenReturn(Optional.of(before));
        when(repository.updateValue(
            SystemConfigService.SYSTEM_TENANT,
            key,
            "true",
            "system-superadmin",
            "完成 P6 独立验收",
            1L)).thenReturn(after);

        SystemConfigItemResponse response = service.update(
            key,
            new SystemConfigUpdateRequest("true", "完成 P6 独立验收", 1L, true),
            "system-superadmin");

        assertThat(response.value()).isEqualTo("true");
        verify(privilegedConfigChangeGuard).assertSystemSuperAdminAllowed("system_config", key);
        verify(repository).updateValue(
            SystemConfigService.SYSTEM_TENANT,
            key,
            "true",
            "system-superadmin",
            "完成 P6 独立验收",
            1L);
    }

    @Test
    void p6AcceptanceDisableKeepsFastFailClosedPathForMfaBoundOperator() {
        String key = SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY;
        SystemConfigItem before =
            configItem(SystemConfigService.SYSTEM_TENANT, key, "true", "P6 正式知识生产独立验收", "API", 2);
        SystemConfigItem after =
            configItem(SystemConfigService.SYSTEM_TENANT, key, "false", "P6 正式知识生产独立验收", "API", 3);
        when(repository.findActive(SystemConfigService.SYSTEM_TENANT, key)).thenReturn(Optional.of(before));
        when(repository.updateValue(
            SystemConfigService.SYSTEM_TENANT,
            key,
            "false",
            "integration-operator",
            "发现风险，立即关闭正式生产",
            2L)).thenReturn(after);

        SystemConfigItemResponse response = service.update(
            key,
            new SystemConfigUpdateRequest("false", "发现风险，立即关闭正式生产", 2L, true),
            "integration-operator");

        assertThat(response.value()).isEqualTo("false");
        verify(privilegedConfigChangeGuard, never()).assertSystemSuperAdminAllowed(anyString(), anyString());
    }

    @Test
    void p6RollbackToEnabledValueAlsoRequiresSystemSuperAdmin() {
        String key = SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY;
        SystemConfigItem before =
            configItem(SystemConfigService.SYSTEM_TENANT, key, "false", "P6 正式知识生产独立验收", "API", 3);
        when(repository.findActive(SystemConfigService.SYSTEM_TENANT, key)).thenReturn(Optional.of(before));
        when(repository.findLatestHistory(SystemConfigService.SYSTEM_TENANT, key))
            .thenReturn(Optional.of(new SystemConfigHistoryEntry(
                SystemConfigService.SYSTEM_TENANT,
                key,
                "true",
                "false",
                "UPDATE",
                3,
                Instant.parse("2026-06-18T10:00:00Z"),
                "integration-operator")));
        doThrow(ApiException.forbidden("当前账号缺少特权配置变更权限"))
            .when(privilegedConfigChangeGuard)
            .assertSystemSuperAdminAllowed("system_config", key);

        assertThatThrownBy(() -> service.rollback(
            key,
            new SystemConfigRollbackRequest("回滚到已放行状态", true),
            "integration-operator"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("内置超级管理员");

        verify(repository, never()).rollbackValue(
            anyString(), anyString(), anyString(), anyString(), anyString());
    }

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

    @Test
    void runtimeKnowledgeRetirementIntervalReadsConfigCenterAndFallsBackSafely() {
        when(repository.findActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.KNOWLEDGE_RETIREMENT_INTERVAL_MS_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                SystemConfigService.KNOWLEDGE_RETIREMENT_INTERVAL_MS_KEY,
                "45000",
                "INTEGER",
                "知识退役扫描间隔",
                "MEDIUM",
                "知识治理组",
                "控制知识身份宽限期到期后的退役扫描间隔。",
                "API",
                false,
                true,
                3,
                null)));

        assertThat(service.runtimeKnowledgeRetirementIntervalMs()).isEqualTo(45_000L);

        when(repository.findActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.KNOWLEDGE_RETIREMENT_INTERVAL_MS_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                SystemConfigService.KNOWLEDGE_RETIREMENT_INTERVAL_MS_KEY,
                "not-a-number",
                "INTEGER",
                "知识退役扫描间隔",
                "MEDIUM",
                "知识治理组",
                "控制知识身份宽限期到期后的退役扫描间隔。",
                "API",
                false,
                true,
                4,
                null)));

        assertThat(service.runtimeKnowledgeRetirementIntervalMs())
            .isEqualTo(SystemConfigService.DEFAULT_KNOWLEDGE_RETIREMENT_INTERVAL_MS);
    }

    @Test
    void runtimeKnowledgeAcquisitionScheduleIntervalReadsConfigCenterAndFallsBackSafely() {
        when(repository.findActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                SystemConfigService.KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS_KEY,
                "60000",
                "INTEGER",
                "公域资料获取调度扫描间隔",
                "MEDIUM",
                "平台知识治理组",
                "控制 AIK-STD-14 公域资料来源到期扫描间隔。",
                "API",
                false,
                true,
                3,
                null)));

        assertThat(service.runtimeKnowledgeAcquisitionScheduleIntervalMs()).isEqualTo(60_000L);

        when(repository.findActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                SystemConfigService.KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS_KEY,
                "not-a-number",
                "INTEGER",
                "公域资料获取调度扫描间隔",
                "MEDIUM",
                "平台知识治理组",
                "控制 AIK-STD-14 公域资料来源到期扫描间隔。",
                "API",
                false,
                true,
                4,
                null)));

        assertThat(service.runtimeKnowledgeAcquisitionScheduleIntervalMs())
            .isEqualTo(SystemConfigService.DEFAULT_KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS);
    }

    @Test
    void runtimeClinicalEventSyncTimeoutReadsConfigCenterAndFallsBackSafely() {
        ClinicalEventWorkerSettings settings = new ClinicalEventWorkerSettings() {
            @Override
            public Duration syncTimeout() {
                return Duration.ofMillis(3_000);
            }

            @Override
            public long workerPollIntervalMs() {
                return 200;
            }
        };
        when(repository.findActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.CLINICAL_EVENT_SYNC_TIMEOUT_MS_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                SystemConfigService.CLINICAL_EVENT_SYNC_TIMEOUT_MS_KEY,
                "1200",
                "INTEGER",
                "临床事件同步求值预算",
                "HIGH",
                "信息科 / 运维组",
                "控制临床事件同步触发规则、路径和 CDSS 的总时延预算。",
                "API",
                true,
                true,
                3,
                null)));

        assertThat(service.runtimeClinicalEventSyncTimeoutMs(settings)).isEqualTo(1_200L);

        when(repository.findActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.CLINICAL_EVENT_SYNC_TIMEOUT_MS_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                SystemConfigService.CLINICAL_EVENT_SYNC_TIMEOUT_MS_KEY,
                "not-a-number",
                "INTEGER",
                "临床事件同步求值预算",
                "HIGH",
                "信息科 / 运维组",
                "控制临床事件同步触发规则、路径和 CDSS 的总时延预算。",
                "API",
                true,
                true,
                4,
                null)));

        assertThat(service.runtimeClinicalEventSyncTimeoutMs(settings)).isEqualTo(3_000L);
    }

    @Test
    void runtimeRealtimeCdsTimeoutsReadConfigCenterAndFallBackSafely() {
        RealtimeCdsSettings settings = new RealtimeCdsSettings() {
            @Override
            public Duration defaultTimeout() {
                return Duration.ofMillis(2_000);
            }

            @Override
            public Duration orderSignTimeout() {
                return Duration.ofMillis(1_000);
            }
        };
        when(repository.findActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.REALTIME_CDS_ORDER_SIGN_TIMEOUT_MS_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                SystemConfigService.REALTIME_CDS_ORDER_SIGN_TIMEOUT_MS_KEY,
                "450",
                "INTEGER",
                "开医嘱实时 CDS 硬超时",
                "HIGH",
                "信息科 / 运维组",
                "控制 order-sign 实时 CDS 的同步硬超时。",
                "API",
                true,
                true,
                3,
                null)));
        when(repository.findActive(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.REALTIME_CDS_DEFAULT_TIMEOUT_MS_KEY))
            .thenReturn(Optional.of(new SystemConfigItem(
                SystemConfigService.SYSTEM_TENANT,
                SystemConfigService.REALTIME_CDS_DEFAULT_TIMEOUT_MS_KEY,
                "not-a-number",
                "INTEGER",
                "实时 CDS 默认硬超时",
                "HIGH",
                "信息科 / 运维组",
                "控制非 order-sign 实时 CDS 的同步硬超时。",
                "API",
                true,
                true,
                4,
                null)));

        assertThat(service.runtimeRealtimeCdsOrderSignTimeoutMs(settings)).isEqualTo(450L);
        assertThat(service.runtimeRealtimeCdsDefaultTimeoutMs(settings)).isEqualTo(2_000L);
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
