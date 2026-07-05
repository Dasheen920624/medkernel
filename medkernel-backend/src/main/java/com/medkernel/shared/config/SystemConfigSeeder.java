package com.medkernel.shared.config;

import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.medkernel.shared.audit.persistence.AuditFallbackProperties;
import com.medkernel.shared.runtime.RuntimeProperties;
import com.medkernel.shared.security.AuthCookieProperties;
import com.medkernel.shared.security.AuthJwtProperties;
import com.medkernel.shared.security.AuthMfaProperties;
import com.medkernel.shared.security.AuthSessionProperties;

/**
 * 启动期把 YAML 默认配置导入关系库配置中心。
 */
@Component
public class SystemConfigSeeder implements ApplicationRunner {

    private final RuntimeProperties runtimeProperties;
    private final AuthJwtProperties jwtProperties;
    private final AuthCookieProperties cookieProperties;
    private final AuthSessionProperties sessionProperties;
    private final AuthMfaProperties mfaProperties;
    private final AuditFallbackProperties auditFallbackProperties;
    private final ClinicalEventWorkerSettings clinicalEventProperties;
    private final RealtimeCdsSettings realtimeCdsSettings;
    private final IntegrationHealthProbeSettings integrationHealthProbeSettings;
    private final Environment environment;
    private final SystemConfigService service;

    public SystemConfigSeeder(RuntimeProperties runtimeProperties,
                              AuthJwtProperties jwtProperties,
                              AuthCookieProperties cookieProperties,
                              AuthSessionProperties sessionProperties,
                              AuthMfaProperties mfaProperties,
                              AuditFallbackProperties auditFallbackProperties,
                              ClinicalEventWorkerSettings clinicalEventProperties,
                              RealtimeCdsSettings realtimeCdsSettings,
                              IntegrationHealthProbeSettings integrationHealthProbeSettings,
                              Environment environment,
                              SystemConfigService service) {
        this.runtimeProperties = runtimeProperties;
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
        this.sessionProperties = sessionProperties;
        this.mfaProperties = mfaProperties;
        this.auditFallbackProperties = auditFallbackProperties;
        this.clinicalEventProperties = clinicalEventProperties;
        this.realtimeCdsSettings = realtimeCdsSettings;
        this.integrationHealthProbeSettings = integrationHealthProbeSettings;
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
        seedKnowledgeLiteraturePolicy(seededAt);
        seedDeploymentFormPolicy(seededAt);
        seedCdssPolicy(seededAt);
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
        seedConfigValue(SystemConfigService.AUTH_MODE_KEY, "PLATFORM",
            "STRING", "认证模式", "HIGH", "安全组",
            "控制平台账号、院方统一身份或双入口认证形态；运行时由配置中心切换。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_JWT_TTL_SECONDS_KEY, Long.toString(jwtProperties.ttlSeconds()),
            "INTEGER", "JWT 有效期", "HIGH", "安全组",
            "控制登录后新签发 JWT 的有效期，变更会影响后续登录会话。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_MFA_ENABLED_KEY, Boolean.toString(mfaProperties.enabled()),
            "BOOLEAN", "登录 MFA", "HIGH", "安全组",
            "控制平台账号登录是否强制完成 TOTP 多因素认证；默认关闭，开启后必须通过真实验证码验证。", true, seededAt);
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
        seedConfigValue(SystemConfigService.AUTH_SESSION_PREFIX + "idle-timeout-seconds",
            Long.toString(sessionProperties.idleTimeoutSeconds()),
            "INTEGER", "无操作自动登出窗口", "HIGH", "安全组",
            "控制登录后前端无操作自动登出的窗口，变更后对后续会话检查热生效。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_SESSION_PREFIX + "warning-seconds",
            Long.toString(sessionProperties.warningSeconds()),
            "INTEGER", "会话超时提醒窗口", "MEDIUM", "安全组",
            "控制无操作自动登出前提前提醒用户续期的秒数。", false, seededAt);
        seedConfigValue(SystemConfigService.AUTH_SESSION_PREFIX + "max-duration-seconds",
            Long.toString(sessionProperties.maxDurationSeconds()),
            "INTEGER", "最大会话时长", "HIGH", "安全组",
            "控制单次登录最多可滑动续期的总时长，超过后必须重新登录。", true, seededAt);
        seedPasswordPolicy(seededAt);
        seedLoginAttemptPolicy(seededAt);
    }

    private void seedPasswordPolicy(Instant seededAt) {
        seedConfigValue(SystemConfigService.AUTH_PASSWORD_PREFIX + "min-length", "12",
            "INTEGER", "口令最小长度", "HIGH", "安全组",
            "控制平台账号、自助改密和初始账号的最小口令长度。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_PASSWORD_PREFIX + "require-uppercase", "true",
            "BOOLEAN", "口令必须包含大写字母", "HIGH", "安全组",
            "控制平台账号口令是否必须包含至少一个大写字母。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_PASSWORD_PREFIX + "require-lowercase", "true",
            "BOOLEAN", "口令必须包含小写字母", "HIGH", "安全组",
            "控制平台账号口令是否必须包含至少一个小写字母。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_PASSWORD_PREFIX + "require-digit", "true",
            "BOOLEAN", "口令必须包含数字", "HIGH", "安全组",
            "控制平台账号口令是否必须包含至少一个数字。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_PASSWORD_PREFIX + "require-symbol", "true",
            "BOOLEAN", "口令必须包含符号", "HIGH", "安全组",
            "控制平台账号口令是否必须包含至少一个非字母数字符号。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_PASSWORD_PREFIX + "hash-algorithm", "BCRYPT",
            "STRING", "口令哈希算法", "HIGH", "安全组",
            "控制平台账号口令哈希算法；默认 BCrypt，国产化形态可切换 SM3。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_PASSWORD_PREFIX + "reset-token-ttl-seconds", "900",
            "INTEGER", "重置一次性凭证有效期", "HIGH", "安全组",
            "控制受控密码重置一次性凭证的有效秒数。", true, seededAt);
    }

    private void seedLoginAttemptPolicy(Instant seededAt) {
        seedConfigValue(SystemConfigService.AUTH_LOGIN_PREFIX + "max-failed-attempts", "5",
            "INTEGER", "连续失败锁定阈值", "HIGH", "安全组",
            "控制同一平台凭证连续登录失败多少次后进入 LOCKED 状态。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_LOGIN_PREFIX + "lockout-seconds", "900",
            "INTEGER", "登录锁定窗口", "HIGH", "安全组",
            "控制连续失败锁定的建议锁定秒数，解除需满足锁定窗口或管理员处理。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_LOGIN_PREFIX + "rate-limit-attempts", "10",
            "INTEGER", "登录限流阈值", "HIGH", "安全组",
            "控制同一机构用户名在限流窗口内允许的失败次数，用于防爆破。", true, seededAt);
        seedConfigValue(SystemConfigService.AUTH_LOGIN_PREFIX + "rate-limit-window-seconds", "60",
            "INTEGER", "登录限流窗口", "HIGH", "安全组",
            "控制登录失败限流的统计时间窗口。", true, seededAt);
    }

    private void seedLoggingPolicy(Instant seededAt) {
        seedConfigValue(SystemConfigService.LOGGING_LEVEL_PREFIX + "root",
            environment.getProperty("logging.level.root", "INFO"),
            "STRING", "基础日志级别", "MEDIUM", "信息科 / 运维组",
            "控制应用基础日志运行级别。", false, seededAt);
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
        seedConfigValue(SystemConfigService.CLINICAL_EVENT_SYNC_TIMEOUT_MS_KEY,
            Long.toString(Math.max(1L, clinicalEventProperties.syncTimeout().toMillis())),
            "INTEGER", "临床事件同步求值预算", "HIGH", "信息科 / 运维组",
            "控制临床事件同步触发规则、路径和 CDSS 的总时延预算，超时必须诚实不可用并进入人工核查。", true, seededAt);
        seedConfigValue(SystemConfigService.REALTIME_CDS_DEFAULT_TIMEOUT_MS_KEY,
            Long.toString(Math.max(1L, realtimeCdsSettings.defaultTimeout().toMillis())),
            "INTEGER", "实时 CDS 默认硬超时", "HIGH", "信息科 / 运维组",
            "控制非 order-sign 实时 CDS 同步取卡预算，超时返回求值不可用并要求人工核查。", true, seededAt);
        seedConfigValue(SystemConfigService.REALTIME_CDS_ORDER_SIGN_TIMEOUT_MS_KEY,
            Long.toString(Math.max(1L, realtimeCdsSettings.orderSignTimeout().toMillis())),
            "INTEGER", "开医嘱实时 CDS 硬超时", "HIGH", "信息科 / 运维组",
            "控制 order-sign 实时 CDS 同步取卡预算，超时返回求值不可用且高危医嘱不静默放过。", true, seededAt);
        seedConfigValue(SystemConfigService.CLINICAL_EVENT_WORKER_POLL_INTERVAL_MS_KEY,
            Long.toString(clinicalEventProperties.workerPollIntervalMs()),
            "INTEGER", "临床事件轮询间隔", "MEDIUM", "信息科 / 运维组",
            "控制临床事件后台任务的轮询间隔，变更后下一轮调度生效。", false, seededAt);
        seedConfigValue(SystemConfigService.INTEGRATION_HEALTH_PROBE_INTERVAL_MS_KEY,
            Long.toString(integrationHealthProbeSettings.healthProbeIntervalMs()),
            "INTEGER", "第三方适配器周期探活间隔", "MEDIUM", "信息科 / 集成组",
            "控制第三方适配器周期健康探测间隔，变更后下一轮调度生效。", false, seededAt);
        seedConfigValue(SystemConfigService.KNOWLEDGE_RETIREMENT_INTERVAL_MS_KEY,
            Long.toString(SystemConfigService.DEFAULT_KNOWLEDGE_RETIREMENT_INTERVAL_MS),
            "INTEGER", "知识退役扫描间隔", "MEDIUM", "知识治理组",
            "控制知识身份宽限期到期后的退役扫描间隔，变更后下一轮调度生效。", false, seededAt);
        seedConfigValue(SystemConfigService.KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS_KEY,
            Long.toString(SystemConfigService.DEFAULT_KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS),
            "INTEGER", "公域资料获取调度扫描间隔", "MEDIUM", "平台知识治理组",
            "控制 AIK-STD-14 公域资料来源到期扫描间隔，变更后下一轮调度生效。", false, seededAt);
    }

    private void seedKnowledgeLiteraturePolicy(Instant seededAt) {
        seedConfigValue(
            SystemConfigService.KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY,
            SystemConfigService.DEFAULT_KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI,
            "STRING",
            "平台知识文献资料库根地址",
            "HIGH",
            "平台知识治理组 / 信息科",
            "主平台知识管理服务器使用的正式文献资料库根地址；初始状态未配置，正式知识生产前必须在配置中心维护受管文件、对象存储或安全网关地址，不得回退未配置的临时目录或工作目录。",
            true,
            "PLATFORM_SEED",
            seededAt);
    }

    private void seedDeploymentFormPolicy(Instant seededAt) {
        seedConfigValue(
            SystemConfigService.DEPLOYMENT_FORM_KEY,
            SystemConfigService.DEFAULT_DEPLOYMENT_FORM,
            "STRING",
            "部署形态",
            "HIGH",
            "平台管理员",
            "控制本实例部署形态：公网或生产中心可用外部大模型服务，患者上下文必须经模型外调安全闸最小化、核心敏感屏蔽和审计；"
                + "院内运行环境可按授权处理必要患者敏感信息，禁止外部模型服务，仅允许本地模型或无模型主链路。"
                + "默认最严格的院内运行环境，生产中心须在配置中心显式切换。",
            true,
            "PLATFORM_SEED",
            seededAt);
    }

    private void seedCdssPolicy(Instant seededAt) {
        seedConfigValue("medkernel.cdss.fatigue.policy",
            "{\"default\":{},\"scenarios\":{},\"departments\":{},\"departmentScenarios\":{}}",
            "JSON", "CDSS 疲劳治理策略", "MEDIUM", "医务处 / 信息科",
            "按场景、科室或科室+场景配置 CDSS 低价值提醒抑制阈值；红线和高风险提醒不可被抑制。",
            false, seededAt);
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
        seedConfigValue(key, value, valueType, displayName, risk, owner, description, protectedConfig, "YML_SEED",
            seededAt);
    }

    private void seedConfigValue(String key,
                                 String value,
                                 String valueType,
                                 String displayName,
                                 String risk,
                                 String owner,
                                 String description,
                                 boolean protectedConfig,
                                 String source,
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
            source,
            protectedConfig,
            seededAt), "system");
    }
}
