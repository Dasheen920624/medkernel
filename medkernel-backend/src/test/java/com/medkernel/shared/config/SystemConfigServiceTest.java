package com.medkernel.shared.config;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.medkernel.engine.security.bootstrap.MfaPolicyService;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.AuditSafetyGuard;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupReadiness;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeFeatureFlag;
import com.medkernel.shared.runtime.RuntimeProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    private final MfaPolicyService mfaPolicyService = mock(MfaPolicyService.class);
    private final SystemConfigService service = new SystemConfigService(
        repository, auditSafetyGuard, auditRecorder, logLevelManager, mfaPolicyService);

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
}
