package com.medkernel.shared.runtime;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupReadiness;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeDependencyStatus;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeDomesticProfile;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeFeatureFlag;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeJvmMetadata;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeOsMetadata;

/**
 * 系统运维快照与国产化自检业务服务（BASE-07）。
 *
 * <p>提供当前系统运行事实信息的汇聚与转换服务。包括 JVM 元数据、操作系统信息、功能开关状态、外部系统依赖存活状态及备份容灾就绪情况。
 */
@Service
public class RuntimeOperationsService {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String STATUS_NOT_CONNECTED = "NOT_CONNECTED";
    private static final String STATUS_MODEL_DISABLED = "MODEL_DISABLED";

    private final Environment environment;
    private final HealthEndpoint healthEndpoint;
    private final MeterRegistry meterRegistry;
    private final RuntimeProperties properties;
    private final SystemConfigService configService;

    /**
     * 构造函数。
     *
     * @param environment Spring 环境变量上下文
     * @param healthEndpoint Spring Actuator 健康监测端点
     * @param meterRegistry Micrometer 业务指标注册中心
     * @param properties 运维配置属性
     * @param configService 配置中心服务
     */
    public RuntimeOperationsService(Environment environment,
                                    HealthEndpoint healthEndpoint,
                                    MeterRegistry meterRegistry,
                                    RuntimeProperties properties,
                                    SystemConfigService configService) {
        this.environment = environment;
        this.healthEndpoint = healthEndpoint;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.configService = configService;
    }

    /**
     * 构建并返回当前系统的最新全量运维监控及国产化指标快照。
     *
     * @return 运维监控与国产化快照实体
     */
    public RuntimeOperationsSnapshot snapshot() {
        String healthStatus = healthEndpoint.health().getStatus().getCode();
        return new RuntimeOperationsSnapshot(
            environment.getProperty("spring.application.name", "medkernel"),
            properties.getEnvironment(),
            properties.getDeploymentMode(),
            properties.getDatabaseDialect(),
            properties.getMigrationLocation(),
            activeProfiles(),
            healthStatus,
            jvmMetadata(),
            osMetadata(),
            featureFlags(),
            dependencies(healthStatus),
            backupReadiness(),
            domesticProfile(),
            Instant.now()
        );
    }

    private List<String> activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return Arrays.stream(profiles).sorted().toList();
    }

    private RuntimeJvmMetadata jvmMetadata() {
        return new RuntimeJvmMetadata(
            System.getProperty("java.version"),
            System.getProperty("java.vendor"),
            System.getProperty("java.vm.name"),
            environment.getProperty("spring.threads.virtual.enabled", Boolean.class, false),
            Runtime.getRuntime().availableProcessors()
        );
    }

    private RuntimeOsMetadata osMetadata() {
        return new RuntimeOsMetadata(
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.arch")
        );
    }

    private List<RuntimeFeatureFlag> featureFlags() {
        return configService.runtimeFeatureFlags(properties);
    }

    private List<RuntimeDependencyStatus> dependencies(String healthStatus) {
        boolean prometheusReady = meterRegistry.find("medkernel_tenant_onboarding_total").counter() != null;
        boolean graphEnabled = flagEnabled("graph-projection");
        boolean difyEnabled = flagEnabled("dify-workflow");
        boolean externalProviderEnabled = flagEnabled("external-provider");
        return List.of(
            new RuntimeDependencyStatus(
                "database",
                "关系数据库",
                STATUS_UP.equals(healthStatus) ? STATUS_UP : STATUS_DEGRADED,
                properties.getDatabaseDialect() + " · " + properties.getMigrationLocation()
            ),
            new RuntimeDependencyStatus(
                "prometheus",
                "Prometheus 指标",
                prometheusReady ? STATUS_UP : STATUS_DEGRADED,
                prometheusReady ? "业务指标已注册" : "业务指标未注册"
            ),
            new RuntimeDependencyStatus(
                "backup-restore",
                "备份恢复",
                properties.getBackup().isEnabled() ? STATUS_DEGRADED : STATUS_NOT_CONNECTED,
                properties.getBackup().isEnabled()
                    ? properties.getBackup().getChecksumPolicy() + "；尚未附带本次恢复演练结果，不标记 UP"
                    : "备份策略未启用；" + properties.getBackup().getChecksumPolicy()
            ),
            new RuntimeDependencyStatus(
                "graph-projection",
                "知识图谱投影",
                graphEnabled ? STATUS_DEGRADED : STATUS_NOT_CONNECTED,
                graphEnabled ? "Feature Flag 已开启；真实图谱探活未接入，不标记 UP" : "Feature Flag 关闭，未连接图谱投影"
            ),
            new RuntimeDependencyStatus(
                "dify-workflow",
                "Dify 工作流",
                difyEnabled ? STATUS_DEGRADED : STATUS_MODEL_DISABLED,
                difyEnabled ? "Feature Flag 已开启；真实模型工作流探活未接入，不标记 UP" : "Feature Flag 关闭，模型工作流未启用"
            ),
            new RuntimeDependencyStatus(
                "model-gateway",
                "模型 Provider",
                externalProviderEnabled ? STATUS_DEGRADED : STATUS_MODEL_DISABLED,
                externalProviderEnabled
                    ? "外部 Provider 开关已开启；真实模型 Provider 探活未接入，不标记 UP"
                    : "外部 Provider 开关关闭，模型能力按 B0 主链路降级"
            ),
            new RuntimeDependencyStatus(
                "external-provider",
                "外部系统 Provider",
                externalProviderEnabled ? STATUS_DEGRADED : STATUS_NOT_CONNECTED,
                externalProviderEnabled
                    ? "外部 Provider 开关已开启；真实外部系统探活未接入，不标记 UP"
                    : "外部 Provider 开关关闭，未连接 HIS/EMR/时间戳等外部系统"
            )
        );
    }

    private boolean flagEnabled(String key) {
        return configService.runtimeFeatureFlagEnabled(properties, key);
    }

    private RuntimeBackupReadiness backupReadiness() {
        RuntimeProperties.Backup backup = properties.getBackup();
        return new RuntimeBackupReadiness(
            backup.isEnabled(),
            backup.getRpo(),
            backup.getRto(),
            backup.getBackupScript(),
            backup.getRestoreScript(),
            backup.getChecksumPolicy()
        );
    }

    private RuntimeDomesticProfile domesticProfile() {
        RuntimeProperties.DomesticProfile profile = properties.getDomesticProfile();
        return new RuntimeDomesticProfile(
            profile.getTargetOs(),
            profile.getTargetJdk(),
            profile.getDatabaseVendors(),
            profile.getCryptoAlgorithms(),
            profile.getEvidence()
        );
    }
}
