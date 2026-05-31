package com.medkernel.shared.config;

import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.medkernel.engine.context.ClinicalEventProperties;
import com.medkernel.shared.audit.persistence.AuditFallbackProperties;
import com.medkernel.shared.runtime.RuntimeProperties;
import com.medkernel.shared.security.AuthCookieProperties;
import com.medkernel.shared.security.AuthJwtProperties;

/**
 * 启动期把 YAML 默认配置导入关系库配置中心。
 */
@Component
public class SystemConfigSeeder implements ApplicationRunner {

    private final RuntimeProperties runtimeProperties;
    private final AuthJwtProperties jwtProperties;
    private final AuthCookieProperties cookieProperties;
    private final AuditFallbackProperties auditFallbackProperties;
    private final ClinicalEventProperties clinicalEventProperties;
    private final Environment environment;
    private final SystemConfigService service;

    public SystemConfigSeeder(RuntimeProperties runtimeProperties,
                              AuthJwtProperties jwtProperties,
                              AuthCookieProperties cookieProperties,
                              AuditFallbackProperties auditFallbackProperties,
                              ClinicalEventProperties clinicalEventProperties,
                              Environment environment,
                              SystemConfigService service) {
        this.runtimeProperties = runtimeProperties;
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
        this.auditFallbackProperties = auditFallbackProperties;
        this.clinicalEventProperties = clinicalEventProperties;
        this.environment = environment;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant seededAt = Instant.now();
        runtimeProperties.getFeatureFlags().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> {
                RuntimeProperties.FeatureFlag flag = entry.getValue();
                service.seed(new SystemConfigSeed(
                    SystemConfigService.SYSTEM_TENANT,
                    SystemConfigService.RUNTIME_FLAG_PREFIX + entry.getKey() + SystemConfigService.RUNTIME_FLAG_SUFFIX,
                    Boolean.toString(flag.isEnabled()),
                    "BOOLEAN",
                    flag.getDisplayName(),
                    flag.getRisk(),
                    flag.getOwner(),
                    flag.getDescription(),
                    "YML_SEED",
                    "HIGH".equalsIgnoreCase(flag.getRisk()),
                    seededAt), "system");
            });
        seedBackupPolicy(seededAt);
        seedAuthPolicy(seededAt);
        seedLoggingPolicy(seededAt);
        seedRuntimeBoundaryPolicy(seededAt);
        service.applyRuntimeLogLevels();
    }

    private void seedBackupPolicy(Instant seededAt) {
        RuntimeProperties.Backup backup = runtimeProperties.getBackup();
        seedBackupValue("enabled", Boolean.toString(backup.isEnabled()), "BOOLEAN", "备份策略启用", "MEDIUM",
            "控制运行形态是否启用数据库备份策略，启用后仍需真实恢复演练证据。", seededAt);
        seedBackupValue("rpo", backup.getRpo(), "STRING", "备份 RPO", "MEDIUM",
            "配置备份恢复点目标，用于运维验收和恢复演练。", seededAt);
        seedBackupValue("rto", backup.getRto(), "STRING", "备份 RTO", "MEDIUM",
            "配置恢复时间目标，用于运维验收和恢复演练。", seededAt);
        seedBackupValue("backup-script", backup.getBackupScript(), "STRING", "备份脚本", "MEDIUM",
            "当前部署形态使用的真实备份脚本路径。", seededAt);
        seedBackupValue("restore-script", backup.getRestoreScript(), "STRING", "恢复脚本", "MEDIUM",
            "当前部署形态使用的真实恢复脚本路径。", seededAt);
        seedBackupValue("checksum-policy", backup.getChecksumPolicy(), "STRING", "备份校验策略", "MEDIUM",
            "备份文件生成与恢复前校验使用的摘要策略。", seededAt);
    }

    private void seedBackupValue(String key,
                                 String value,
                                 String valueType,
                                 String displayName,
                                 String risk,
                                 String description,
                                 Instant seededAt) {
        service.seed(new SystemConfigSeed(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.RUNTIME_BACKUP_PREFIX + key,
            value,
            valueType,
            displayName,
            risk,
            "信息科 / 运维组",
            description,
            "YML_SEED",
            false,
            seededAt), "system");
    }

    private void seedAuthPolicy(Instant seededAt) {
        seedConfigValue(SystemConfigService.AUTH_JWT_TTL_SECONDS_KEY, Long.toString(jwtProperties.ttlSeconds()),
            "INTEGER", "JWT 有效期", "HIGH", "安全组",
            "控制登录后新签发 JWT 的有效期，变更会影响后续登录会话。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_COOKIE_PREFIX + "name", cookieProperties.name(),
            "STRING", "登录 Cookie 名称", "HIGH", "安全组",
            "控制登录态 Cookie 名称，变更需确认前后端与网关策略。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_COOKIE_PREFIX + "secure", Boolean.toString(cookieProperties.secure()),
            "BOOLEAN", "Cookie Secure 策略", "HIGH", "安全组",
            "控制登录态 Cookie 是否仅允许 HTTPS 传输。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_COOKIE_PREFIX + "same-site", cookieProperties.sameSite(),
            "STRING", "Cookie SameSite 策略", "HIGH", "安全组",
            "控制登录态 Cookie 的跨站携带策略。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_COOKIE_PREFIX + "path", cookieProperties.path(),
            "STRING", "Cookie Path 策略", "MEDIUM", "安全组",
            "控制登录态 Cookie 的可见路径。", false, seededAt);
        seedConfigValue(SystemConfigService.AUTH_COOKIE_PREFIX + "max-age-seconds",
            Long.toString(cookieProperties.maxAgeSeconds()),
            "INTEGER", "Cookie 有效期", "HIGH", "安全组",
            "控制登录态 Cookie 的浏览器保存时长。", true, seededAt);
    }

    private void seedLoggingPolicy(Instant seededAt) {
        seedConfigValue(SystemConfigService.LOGGING_LEVEL_PREFIX + "root",
            environment.getProperty("logging.level.root", "INFO"),
            "STRING", "Root 日志级别", "MEDIUM", "信息科 / 运维组",
            "控制应用 Root Logger 运行级别。", false, seededAt);
        seedConfigValue(SystemConfigService.LOGGING_LEVEL_PREFIX + "com.medkernel",
            environment.getProperty("logging.level.com.medkernel", "INFO"),
            "STRING", "MedKernel 日志级别", "MEDIUM", "信息科 / 运维组",
            "控制 MedKernel 业务包运行日志级别。", false, seededAt);
    }

    private void seedRuntimeBoundaryPolicy(Instant seededAt) {
        seedConfigValue(SystemConfigService.AUDIT_FALLBACK_PATH_KEY,
            auditFallbackProperties.pathOrDefault(),
            "STRING", "审计降级文件路径", "HIGH", "合规审计",
            "控制审计持久化不可用时 JSONL 降级证据写入路径。", true, seededAt);
        seedConfigValue(SystemConfigService.CLINICAL_EVENT_WORKER_POLL_INTERVAL_MS_KEY,
            Long.toString(clinicalEventProperties.workerPollIntervalMs()),
            "INTEGER", "临床事件轮询间隔", "MEDIUM", "信息科 / 运维组",
            "控制临床事件 outbox worker 的轮询间隔，变更后下一轮调度生效。", false, seededAt);
    }

    private void seedConfigValue(String key,
                                 String value,
                                 String valueType,
                                 String displayName,
                                 String risk,
                                 String owner,
                                 String description,
                                 boolean protectedConfig,
                                 Instant seededAt) {
        service.seed(new SystemConfigSeed(
            SystemConfigService.SYSTEM_TENANT,
            key,
            value,
            valueType,
            displayName,
            risk,
            owner,
            description,
            "YML_SEED",
            protectedConfig,
            seededAt), "system");
    }
}
