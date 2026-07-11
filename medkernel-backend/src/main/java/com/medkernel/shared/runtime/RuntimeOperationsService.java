package com.medkernel.shared.runtime;

import java.security.Security;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupReadiness;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeDomesticCheckItem;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeDomesticCompatibility;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeDependencyStatus;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeDomesticProfile;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeFeatureFlag;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeJvmMetadata;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeOsMetadata;

/**
 * 系统运维快照与国产化适配自检业务服务（BASE-07）。
 *
 * <p>提供当前系统运行事实信息的汇聚与转换服务。包括 JVM 元数据、操作系统信息、功能开关状态、外部系统依赖存活状态及备份容灾就绪情况。
 */
@Service
public class RuntimeOperationsService {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String STATUS_NOT_CONNECTED = "NOT_CONNECTED";
    private static final String STATUS_MODEL_DISABLED = "MODEL_DISABLED";
    private static final String CHECK_PASS = "PASS";
    private static final String CHECK_WARN = "WARN";
    private static final String CHECK_FAIL = "FAIL";
    private static final String CHECK_UNKNOWN = "UNKNOWN";

    private final Environment environment;
    private final HealthEndpoint healthEndpoint;
    private final MeterRegistry meterRegistry;
    private final RuntimeProperties properties;
    private final SystemConfigService configService;
    private final RuntimeBackupDrillEvidenceReader backupDrillEvidenceReader;

    /**
     * 构造函数。
     *
     * @param environment Spring 环境变量上下文
     * @param healthEndpoint Spring Actuator 健康监测端点
     * @param meterRegistry Micrometer 业务指标注册中心
     * @param properties 运维配置属性
     * @param configService 配置中心服务
     * @param backupDrillEvidenceReader 部署侧恢复演练证据读取器
     */
    public RuntimeOperationsService(Environment environment,
                                    HealthEndpoint healthEndpoint,
                                    MeterRegistry meterRegistry,
                                    RuntimeProperties properties,
                                    SystemConfigService configService,
                                    RuntimeBackupDrillEvidenceReader backupDrillEvidenceReader) {
        this.environment = environment;
        this.healthEndpoint = healthEndpoint;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.configService = configService;
        this.backupDrillEvidenceReader = backupDrillEvidenceReader;
    }

    /**
     * 构建并返回当前系统的最新全量运维监控及国产化指标快照。
     *
     * @return 运维监控与国产化快照实体
     */
    public RuntimeOperationsSnapshot snapshot() {
        String healthStatus = healthEndpoint.health().getStatus().getCode();
        RuntimeBackupReadiness backup = backupReadiness();
        RuntimeJvmMetadata jvm = jvmMetadata();
        RuntimeOsMetadata os = osMetadata();
        List<RuntimeDependencyStatus> dependencies = dependencies(healthStatus, backup);
        RuntimeDomesticProfile domesticProfile = domesticProfile();
        return new RuntimeOperationsSnapshot(
            environment.getProperty("spring.application.name", "medkernel"),
            properties.getEnvironment(),
            properties.getDeploymentMode(),
            properties.getDatabaseDialect(),
            properties.getMigrationLocation(),
            activeProfiles(),
            healthStatus,
            jvm,
            os,
            featureFlags(),
            dependencies,
            backup,
            domesticProfile,
            domesticCompatibility(domesticProfile, jvm, os),
            Instant.now()
        );
    }

    /**
     * 生成国产化适配自检报告文本。
     *
     * <p>报告内容仅来自 {@link #snapshot()} 暴露的安全字段，避免输出密钥、口令或内网连接串。
     *
     * @return 国产化适配自检文本报告
     */
    public String domesticReport() {
        RuntimeOperationsSnapshot snapshot = snapshot();
        RuntimeDomesticCompatibility compatibility = snapshot.domesticCompatibility();
        StringBuilder report = new StringBuilder();
        report.append("MedKernel 国产化适配自检报告\n");
        report.append("服务: ").append(snapshot.serviceName()).append('\n');
        report.append("环境: ").append(snapshot.environment()).append(" / ")
            .append(snapshot.deploymentMode()).append('\n');
        report.append("生成时间: ").append(snapshot.generatedAt()).append('\n');
        report.append("整体状态: ").append(compatibility.overallStatus()).append('\n');
        report.append("摘要: ").append(compatibility.summary()).append("\n\n");
        for (RuntimeDomesticCheckItem item : compatibility.items()) {
            report.append("[").append(item.status()).append("] ")
                .append(item.displayName()).append(" (").append(item.category()).append(")\n")
                .append("实际: ").append(item.actualValue()).append('\n')
                .append("目标: ").append(item.expectedValue()).append('\n')
                .append("原因: ").append(item.reason()).append('\n')
                .append("建议: ").append(item.recommendation()).append('\n')
                .append("证据: ").append(item.evidence()).append("\n\n");
        }
        return report.toString();
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

    private List<RuntimeDependencyStatus> dependencies(String healthStatus, RuntimeBackupReadiness backup) {
        boolean prometheusReady = meterRegistry.find("medkernel_tenant_onboarding_total").counter() != null;
        boolean graphEnabled = flagEnabled("graph-projection");
        boolean searchEnabled = flagEnabled("search-projection");
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
                backupStatus(backup),
                backupDetail(backup)
            ),
            new RuntimeDependencyStatus(
                "graph-projection",
                "知识图谱投影",
                graphEnabled ? STATUS_DEGRADED : STATUS_NOT_CONNECTED,
                graphEnabled ? "能力开关已开启；知识图谱连接健康验证未通过，保持降级状态" : "能力开关关闭，未连接图谱投影"
            ),
            new RuntimeDependencyStatus(
                "search-projection",
                "知识搜索投影",
                searchEnabled ? STATUS_DEGRADED : STATUS_NOT_CONNECTED,
                searchEnabled ? "能力开关已开启；知识搜索连接健康验证未通过，保持降级状态" : "能力开关关闭，未连接搜索投影"
            ),
            new RuntimeDependencyStatus(
                "dify-workflow",
                "模型工作流",
                difyEnabled ? STATUS_DEGRADED : STATUS_MODEL_DISABLED,
                difyEnabled ? "能力开关已开启；模型工作流连接健康验证未通过，保持降级状态" : "能力开关关闭，模型工作流未启用"
            ),
            new RuntimeDependencyStatus(
                "model-gateway",
                "模型服务",
                externalProviderEnabled ? STATUS_DEGRADED : STATUS_MODEL_DISABLED,
                externalProviderEnabled
                    ? "外部模型服务开关已开启；请在模型能力与安全页完成提供方健康验证，保持降级状态"
                    : "外部模型服务开关关闭，模型能力按无模型规则主链路运行"
            ),
            new RuntimeDependencyStatus(
                "external-provider",
                "外部系统连接",
                externalProviderEnabled ? STATUS_DEGRADED : STATUS_NOT_CONNECTED,
                externalProviderEnabled
                    ? "外部系统连接开关已开启；请在服务对接页完成连接健康验证，保持降级状态"
                    : "外部系统连接开关关闭，未连接 HIS/EMR/时间戳等外部系统"
            )
        );
    }

    private boolean flagEnabled(String key) {
        return configService.runtimeFeatureFlagEnabled(properties, key);
    }

    private String backupStatus(RuntimeBackupReadiness backup) {
        if (!backup.enabled()) {
            return STATUS_NOT_CONNECTED;
        }
        return "SUCCESS".equals(backup.drillEvidence().status()) ? STATUS_UP : STATUS_DEGRADED;
    }

    private String backupDetail(RuntimeBackupReadiness backup) {
        if (!backup.enabled()) {
            return "备份策略未启用；" + backup.checksumPolicy();
        }
        if ("SUCCESS".equals(backup.drillEvidence().status())) {
            return backup.drillEvidence().detail();
        }
        return backup.checksumPolicy() + "；" + backup.drillEvidence().detail() + "，不标记 UP";
    }

    private RuntimeBackupReadiness backupReadiness() {
        RuntimeBackupReadiness configured = configService.runtimeBackupReadiness(properties);
        return new RuntimeBackupReadiness(
            configured.enabled(),
            configured.rpo(),
            configured.rto(),
            configured.backupScript(),
            configured.restoreScript(),
            configured.checksumPolicy(),
            backupDrillEvidenceReader.read(
                properties.getBackup().getDrillEvidenceFile(),
                currentDatabaseName()
            ),
            configured.source(),
            configured.warning()
        );
    }

    private String currentDatabaseName() {
        String jdbcUrl = environment.getProperty("spring.datasource.url", "");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "";
        }
        String withoutQuery = jdbcUrl.split("[?;]", 2)[0];
        int slash = withoutQuery.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < withoutQuery.length()) {
            return withoutQuery.substring(slash + 1).trim();
        }
        int colon = withoutQuery.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < withoutQuery.length()) {
            return withoutQuery.substring(colon + 1).trim();
        }
        return "";
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

    private RuntimeDomesticCompatibility domesticCompatibility(RuntimeDomesticProfile profile,
                                                               RuntimeJvmMetadata jvm,
                                                               RuntimeOsMetadata os) {
        List<RuntimeDomesticCheckItem> items = List.of(
            osCheck(profile, os),
            jdkCheck(profile, jvm),
            databaseCheck(profile),
            cryptoProviderCheck(profile),
            middlewareCheck(),
            browserCheck(),
            caCheck()
        );
        return new RuntimeDomesticCompatibility(
            overallDomesticStatus(items),
            domesticSummary(items),
            items,
            Instant.now()
        );
    }

    private RuntimeDomesticCheckItem osCheck(RuntimeDomesticProfile profile, RuntimeOsMetadata os) {
        String actual = safe(os.name()) + " " + safe(os.version()) + " " + safe(os.arch());
        boolean matched = containsAnyTargetToken(actual, profile.targetOs());
        return domesticItem(
            "os",
            "OS",
            "操作系统",
            matched ? CHECK_PASS : CHECK_WARN,
            actual.strip(),
            profile.targetOs(),
            matched
                ? "当前操作系统命中国产化目标清单。"
                : "当前操作系统未命中国产化目标清单，不标记通过。",
            matched
                ? "保留当前目标机自检报告作为交付证据。"
                : "在麒麟、统信或 openEuler 目标机重新运行自检，或修正 medkernel.runtime.domestic-profile.target-os。",
            "System.getProperty(os.name/os.version/os.arch)"
        );
    }

    private RuntimeDomesticCheckItem jdkCheck(RuntimeDomesticProfile profile, RuntimeJvmMetadata jvm) {
        String actual = safe(jvm.javaVendor()) + " · " + safe(jvm.javaVersion()) + " · " + safe(jvm.vmName());
        int actualMajor = javaMajor(jvm.javaVersion());
        int targetMajor = firstNumber(profile.targetJdk());
        boolean versionTooLow = targetMajor > 0 && actualMajor > 0 && actualMajor < targetMajor;
        boolean vendorMatched = containsAnyTargetToken(jvm.javaVendor(), profile.targetJdk())
            || containsAnyTargetToken(jvm.vmName(), profile.targetJdk());
        String status = versionTooLow ? CHECK_FAIL : vendorMatched ? CHECK_PASS : CHECK_WARN;
        return domesticItem(
            "jdk",
            "JDK",
            "JDK 运行时",
            status,
            actual.strip(),
            profile.targetJdk(),
            switch (status) {
                case CHECK_PASS -> "当前 JDK 厂商命中国产化目标清单。";
                case CHECK_FAIL -> "当前 JDK 主版本低于国产化目标要求。";
                default -> "当前 JDK 厂商未命中国产化目标清单，不标记通过。";
            },
            switch (status) {
                case CHECK_PASS -> "保留当前 JDK 版本证据，随部署包归档。";
                case CHECK_FAIL -> "升级到目标国产 JDK 主版本后重新运行自检。";
                default -> "在 KAE-JDK 或 BiSheng JDK 目标运行时重新运行自检。";
            },
            "System.getProperty(java.vendor/java.version/java.vm.name)"
        );
    }

    private RuntimeDomesticCheckItem databaseCheck(RuntimeDomesticProfile profile) {
        String vendor = databaseVendor(properties.getDatabaseDialect());
        String actual = safe(properties.getDatabaseDialect()) + " · " + safe(properties.getMigrationLocation());
        boolean matched = containsIgnoreCase(profile.databaseVendors(), vendor)
            || containsIgnoreCase(profile.databaseVendors(), properties.getDatabaseDialect());
        return domesticItem(
            "database",
            "DATABASE",
            "关系数据库",
            matched ? CHECK_PASS : CHECK_WARN,
            actual.strip(),
            String.join(" / ", profile.databaseVendors()),
            matched
                ? "当前数据库方言命中国产化目标清单。"
                : "当前数据库方言未命中国产化目标清单，不标记通过。",
            matched
                ? "保留五方言迁移 smoke 与当前 profile 证据。"
                : "切换 dm 或 kingbase profile，并重新运行五方言迁移 smoke。",
            "medkernel.runtime.database-dialect + medkernel.runtime.migration-location"
        );
    }

    private RuntimeDomesticCheckItem cryptoProviderCheck(RuntimeDomesticProfile profile) {
        Set<String> messageDigests = Security.getAlgorithms("MessageDigest");
        Set<String> ciphers = Security.getAlgorithms("Cipher");
        Set<String> signatures = Security.getAlgorithms("Signature");
        boolean sm3 = containsAlgorithm(messageDigests, "SM3");
        boolean sm4 = containsAlgorithm(ciphers, "SM4");
        boolean sm2 = containsAlgorithm(signatures, "SM3WITHSM2") || containsAlgorithm(ciphers, "SM2");
        boolean allRequired = true;
        for (String algorithm : profile.cryptoAlgorithms()) {
            String normalized = normalize(algorithm);
            if (normalized.equals("SM3")) {
                allRequired &= sm3;
            } else if (normalized.equals("SM4")) {
                allRequired &= sm4;
            } else if (normalized.equals("SM2")) {
                allRequired &= sm2;
            }
        }
        String actual = "SM2=" + yesNo(sm2) + " / SM3=" + yesNo(sm3) + " / SM4=" + yesNo(sm4);
        return domesticItem(
            "crypto-provider",
            "CRYPTO",
            "国密算法组件",
            allRequired ? CHECK_PASS : CHECK_WARN,
            actual,
            String.join(" / ", profile.cryptoAlgorithms()),
            allRequired
                ? "当前运行环境已注册所需国密算法组件。"
                : "国密算法组件未全部注册，保持告警状态。",
            allRequired
                ? "保留国密自检与组件版本证据。"
                : "确认 BouncyCastle 或院方国密组件已加载，并运行 SM2/SM3/SM4 自检。",
            "java.security.Security.getAlgorithms"
        );
    }

    private RuntimeDomesticCheckItem middlewareCheck() {
        boolean govcloud = activeProfiles().stream().anyMatch(profile -> normalize(profile).contains("GOVCLOUD"))
            || normalize(properties.getDeploymentMode()).contains("GOVCLOUD");
        return domesticItem(
            "middleware",
            "MIDDLEWARE",
            "中间件与部署形态",
            govcloud ? CHECK_PASS : CHECK_UNKNOWN,
            properties.getDeploymentMode() + " · profiles=" + String.join("/", activeProfiles()),
            "国产化交付 profile / 目标中间件探活",
            govcloud
                ? "当前已启用国产化交付 profile。"
                : "当前未启用国产化交付 profile，且没有真实中间件探活，不标记通过。",
            govcloud
                ? "保留部署 profile 与中间件健康检查证据。"
                : "在交付环境启用 govcloud/onprem profile，并接入目标中间件健康检查。",
            "Spring activeProfiles + medkernel.runtime.deployment-mode"
        );
    }

    private RuntimeDomesticCheckItem browserCheck() {
        return domesticItem(
            "browser",
            "BROWSER",
            "国产浏览器",
            CHECK_UNKNOWN,
            "服务端快照无法读取客户端浏览器",
            "国产浏览器现场版本",
            "服务端无法读取客户端浏览器，不标记通过。",
            "在交付现场用目标国产浏览器打开本页，保存浏览器 UA 与页面验收截图。",
            "现场浏览器验收"
        );
    }

    private RuntimeDomesticCheckItem caCheck() {
        return domesticItem(
            "ca",
            "CA",
            "国密 CA / 身份证书链",
            CHECK_UNKNOWN,
            "未检测到院方国密 CA 连接器",
            "院方国密 CA / OIDC / SAML 证书链",
            "当前运行状态快照未发现真实院方国密 CA 连接器，不标记通过。",
            "配置真实 IdP/CA 连接器并完成证书链探活后重新导出报告。",
            "运行时依赖与身份委托配置"
        );
    }

    private RuntimeDomesticCheckItem domesticItem(String key,
                                                  String category,
                                                  String displayName,
                                                  String status,
                                                  String actualValue,
                                                  String expectedValue,
                                                  String reason,
                                                  String recommendation,
                                                  String evidence) {
        return new RuntimeDomesticCheckItem(
            key,
            category,
            displayName,
            status,
            safe(actualValue),
            safe(expectedValue),
            safe(reason),
            safe(recommendation),
            safe(evidence)
        );
    }

    private String overallDomesticStatus(List<RuntimeDomesticCheckItem> items) {
        if (items.stream().anyMatch(item -> CHECK_FAIL.equals(item.status()))) {
            return CHECK_FAIL;
        }
        if (items.stream().anyMatch(item -> CHECK_WARN.equals(item.status()) || CHECK_UNKNOWN.equals(item.status()))) {
            return CHECK_WARN;
        }
        return CHECK_PASS;
    }

    private String domesticSummary(List<RuntimeDomesticCheckItem> items) {
        long pass = items.stream().filter(item -> CHECK_PASS.equals(item.status())).count();
        long warn = items.stream().filter(item -> CHECK_WARN.equals(item.status())).count();
        long fail = items.stream().filter(item -> CHECK_FAIL.equals(item.status())).count();
        long unknown = items.stream().filter(item -> CHECK_UNKNOWN.equals(item.status())).count();
        return pass + " 项通过，" + warn + " 项警告，" + fail + " 项失败，" + unknown + " 项待现场确认";
    }

    private boolean containsAnyTargetToken(String actual, String expected) {
        String normalizedActual = normalize(actual);
        return Arrays.stream(safe(expected).split("[/、,，\\s]+"))
            .map(String::strip)
            .filter(token -> token.length() > 1)
            .filter(token -> !normalize(token).equals("JDK"))
            .filter(token -> !normalize(token).matches("\\d+"))
            .map(this::normalize)
            .anyMatch(normalizedActual::contains);
    }

    private boolean containsIgnoreCase(List<String> values, String candidate) {
        String normalizedCandidate = normalize(candidate);
        return values.stream().map(this::normalize).anyMatch(value -> value.equals(normalizedCandidate));
    }

    private String databaseVendor(String dialect) {
        return switch (normalize(dialect)) {
            case "DM", "DAMENG" -> "达梦";
            case "KINGBASE", "KINGBASEES" -> "人大金仓";
            case "POSTGRES", "POSTGRESQL" -> "PostgreSQL";
            case "ORACLE" -> "Oracle";
            case "H2" -> "H2";
            default -> safe(dialect);
        };
    }

    private boolean containsAlgorithm(Set<String> algorithms, String expected) {
        String normalizedExpected = normalize(expected);
        return algorithms.stream().map(this::normalize).anyMatch(algorithm -> algorithm.equals(normalizedExpected));
    }

    private int firstNumber(String value) {
        for (String token : safe(value).split("\\D+")) {
            if (!token.isBlank()) {
                return Integer.parseInt(token);
            }
        }
        return 0;
    }

    private int javaMajor(String javaVersion) {
        String value = safe(javaVersion);
        if (value.startsWith("1.")) {
            return firstNumber(value.substring(2));
        }
        return firstNumber(value);
    }

    private String normalize(String value) {
        return safe(value).replace("-", "").replace("_", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String yesNo(boolean value) {
        return value ? "已注册" : "未注册";
    }
}
