package com.medkernel.shared.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditConfigChangeCommand;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.AuditSafetyGuard;
import com.medkernel.shared.audit.persistence.AuditFallbackProperties;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupReadiness;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeFeatureFlag;
import com.medkernel.shared.runtime.RuntimeProperties;
import com.medkernel.shared.security.AuthCookieProperties;
import com.medkernel.shared.security.AuthJwtProperties;
import com.medkernel.shared.security.AuthMode;
import com.medkernel.shared.security.AuthSessionProperties;

/**
 * 配置中心应用服务，负责配置读写、变更历史和运行底座热生效。
 */
@Service
public class SystemConfigService {

    static final String SYSTEM_TENANT = "SYSTEM";
    static final String RUNTIME_FLAG_PREFIX = "medkernel.runtime.feature-flags.";
    static final String RUNTIME_FLAG_SUFFIX = ".enabled";
    static final String RUNTIME_BACKUP_PREFIX = "medkernel.runtime.backup.";
    public static final String AUTH_JWT_TTL_SECONDS_KEY = "medkernel.auth.jwt.ttl-seconds";
    public static final String AUTH_MODE_KEY = "medkernel.auth.mode";
    public static final String AUTH_MFA_ENABLED_KEY = "medkernel.auth.mfa.enabled";
    public static final String AUTH_COOKIE_PREFIX = "medkernel.auth.cookie.";
    public static final String AUTH_SESSION_PREFIX = "medkernel.auth.session.";
    public static final String AUTH_PASSWORD_PREFIX = "medkernel.auth.password.";
    private static final int MIN_STRONG_PASSWORD_LENGTH = 12;
    public static final String AUTH_LOGIN_PREFIX = "medkernel.auth.login.";
    public static final String LOGGING_LEVEL_PREFIX = "medkernel.logging.level.";
    public static final String AUDIT_FALLBACK_PATH_KEY = "medkernel.audit.fallback.path";
    public static final String CLINICAL_EVENT_SYNC_TIMEOUT_MS_KEY =
        "medkernel.events.sync-timeout-ms";
    public static final String CLINICAL_EVENT_WORKER_POLL_INTERVAL_MS_KEY =
        "medkernel.events.worker.poll-interval-ms";
    public static final String REALTIME_CDS_DEFAULT_TIMEOUT_MS_KEY =
        "medkernel.cdss.realtime.default-timeout-ms";
    public static final String REALTIME_CDS_ORDER_SIGN_TIMEOUT_MS_KEY =
        "medkernel.cdss.realtime.order-sign-timeout-ms";
    public static final String INTEGRATION_HEALTH_PROBE_INTERVAL_MS_KEY =
        "medkernel.integration.health-probe-interval-ms";
    public static final String KNOWLEDGE_RETIREMENT_INTERVAL_MS_KEY =
        "medkernel.knowledge.retirement-interval-ms";
    public static final long DEFAULT_KNOWLEDGE_RETIREMENT_INTERVAL_MS = 300_000L;
    public static final String KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS_KEY =
        "medkernel.knowledge.acquisition.schedule-interval-ms";
    public static final long DEFAULT_KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS = 300_000L;
    public static final String KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY =
        "medkernel.knowledge.literature.material-root-uri";
    public static final String DEFAULT_KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI = "";
    // 部署形态：公网/生产中心形态可接入外部模型服务，患者上下文必须经外调安全闸最小化和核心敏感屏蔽；
    // 院内运行环境可按授权处理必要患者敏感信息，禁外部模型服务，仅本地模型或无模型主链路。
    // 默认取最严格的院内运行环境（安全默认），生产中心须显式配置。
    public static final String DEPLOYMENT_FORM_KEY = "medkernel.deployment.form";
    public static final String DEFAULT_DEPLOYMENT_FORM = "HOSPITAL_RUNTIME";
    private static final Set<String> FORBIDDEN_KNOWLEDGE_LITERATURE_URI_SCHEMES =
        Set.of("tmp", "local", "classpath", "http");
    private static final String SAFE_DEFAULT_SOURCE = "SAFE_DEFAULT";
    private static final Set<String> PROTECTED_RUNTIME_DISABLE_KEYS = Set.of(
        RUNTIME_FLAG_PREFIX + "domestic-crypto" + RUNTIME_FLAG_SUFFIX);

    private final SystemConfigRepository repository;
    private final AuditSafetyGuard auditSafetyGuard;
    private final AuditRecorder auditRecorder;
    private final RuntimeLogLevelManager logLevelManager;
    private final HighRiskChangeGuard highRiskChangeGuard;
    private final SystemConfigSeedWriter seedWriter;

    public SystemConfigService(SystemConfigRepository repository,
                               AuditSafetyGuard auditSafetyGuard,
                               AuditRecorder auditRecorder,
                               RuntimeLogLevelManager logLevelManager,
                               HighRiskChangeGuard highRiskChangeGuard,
                               SystemConfigSeedWriter seedWriter) {
        this.repository = repository;
        this.auditSafetyGuard = auditSafetyGuard;
        this.auditRecorder = auditRecorder;
        this.logLevelManager = logLevelManager;
        this.highRiskChangeGuard = highRiskChangeGuard;
        this.seedWriter = seedWriter;
    }

    public List<SystemConfigItemResponse> list(String prefix) {
        return repository.listActive(SYSTEM_TENANT, prefix).stream()
            .map(SystemConfigItemResponse::from)
            .toList();
    }

    public List<SystemConfigItemResponse> listTenantMerged(String tenantId, String prefix) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        Map<String, SystemConfigItem> tenantItems = repository.listActive(normalizedTenantId, prefix).stream()
            .collect(java.util.stream.Collectors.toMap(
                SystemConfigItem::key,
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new));
        return repository.listActive(SYSTEM_TENANT, prefix).stream()
            .map(systemItem -> tenantItems.getOrDefault(systemItem.key(), inheritedTenantItem(normalizedTenantId, systemItem)))
            .map(SystemConfigItemResponse::from)
            .toList();
    }

    @Transactional
    public SystemConfigItemResponse update(String key, SystemConfigUpdateRequest request, String actor) {
        return updateTenant(SYSTEM_TENANT, key, request, actor);
    }

    @Transactional
    public SystemConfigItemResponse updateTenantOverride(
            String tenantId,
            String key,
            SystemConfigUpdateRequest request,
            String actor) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String normalizedKey = normalizeKey(key);
        if (repository.findActive(normalizedTenantId, normalizedKey).isEmpty()) {
            SystemConfigItem systemItem = repository.findActive(SYSTEM_TENANT, normalizedKey)
                .orElseThrow(() -> ApiException.notFound("配置项 " + normalizedKey));
            Instant now = Instant.now();
            repository.insertSeedIfAbsent(new SystemConfigSeed(
                normalizedTenantId,
                normalizedKey,
                systemItem.value(),
                systemItem.valueType(),
                systemItem.displayName(),
                systemItem.risk(),
                systemItem.owner(),
                systemItem.description(),
                "SYSTEM_INHERITED",
                systemItem.protectedConfig(),
                now), currentActor(actor));
            request = new SystemConfigUpdateRequest(
                request.value(),
                request.reason(),
                null,
                request.confirmedHighRisk());
        }
        return updateTenant(normalizedTenantId, normalizedKey, request, actor);
    }

    /**
     * 读取租户级配置；缺失时只插入调用方给出的安全默认值，不覆盖已有真实配置。
     */
    @Transactional
    public SystemConfigItemResponse getOrSeedTenantConfig(
            String tenantId,
            String key,
            SystemConfigSeed seed,
            String actor) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String normalizedKey = normalizeKey(key);
        SystemConfigItem current = repository.findActive(normalizedTenantId, normalizedKey).orElse(null);
        if (current != null) {
            return SystemConfigItemResponse.from(current);
        }
        SystemConfigSeed normalizedSeed = new SystemConfigSeed(
            normalizedTenantId,
            normalizedKey,
            seed.value(),
            seed.valueType(),
            seed.displayName(),
            seed.risk(),
            seed.owner(),
            seed.description(),
            seed.source(),
            seed.protectedConfig(),
            seed.seededAt());
        seedWriter.insertSeedIfAbsent(normalizedSeed, currentActor(actor));
        return repository.findActive(normalizedTenantId, normalizedKey)
            .map(SystemConfigItemResponse::from)
            .orElseThrow(() -> ApiException.notFound("配置项 " + normalizedKey));
    }

    /**
     * 更新租户级配置，复用配置中心的版本、历史、审计和高风险护栏。
     */
    @Transactional
    public SystemConfigItemResponse updateTenant(
            String tenantId,
            String key,
            SystemConfigUpdateRequest request,
            String actor) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String normalizedKey = normalizeKey(key);
        String value = normalizeValue(request.value());
        SystemConfigItem before = repository.findActive(normalizedTenantId, normalizedKey)
            .orElseThrow(() -> ApiException.notFound("配置项 " + normalizedKey));
        validateValue(before, value);
        auditSafetyGuard.assertChangeAllowed(
            new AuditConfigChangeCommand(normalizedKey, before.value(), value, request.reason()));
        assertProtectedRuntimeDisableAllowed(before, value, request.reason());
        assertHighRiskChangeConfirmed(before, request.reason(), request.confirmedHighRisk());
        assertHighRiskMfaBound(before);
        SystemConfigItem after = repository.updateValue(
            normalizedTenantId,
            normalizedKey,
            value,
            currentActor(actor),
            request.reason(),
            request.expectedVersion());
        String targetId = SYSTEM_TENANT.equals(normalizedTenantId)
            ? normalizedKey
            : normalizedTenantId + "/" + normalizedKey;
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.UPDATE,
            "system_config",
            targetId,
            "更新配置项：" + targetId,
            snapshot(before, request.reason()),
            snapshot(after, request.reason()),
            null));
        applyRuntimeSideEffect(after);
        return SystemConfigItemResponse.from(after);
    }

    @Transactional
    public SystemConfigItemResponse rollback(String key, SystemConfigRollbackRequest request, String actor) {
        String normalizedKey = normalizeKey(key);
        String reason = normalizeReason(request.reason());
        SystemConfigItem before = repository.findActive(SYSTEM_TENANT, normalizedKey)
            .orElseThrow(() -> ApiException.notFound("配置项 " + normalizedKey));
        SystemConfigHistoryEntry latest = repository.findLatestHistory(SYSTEM_TENANT, normalizedKey)
            .orElseThrow(() -> ApiException.conflict("配置项没有可回滚的历史版本"));
        String targetValue = normalizeRollbackValue(normalizedKey, latest.beforeValue());
        validateValue(before, targetValue);
        auditSafetyGuard.assertChangeAllowed(
            new AuditConfigChangeCommand(normalizedKey, before.value(), targetValue, reason));
        assertProtectedRuntimeDisableAllowed(before, targetValue, reason);
        assertHighRiskChangeConfirmed(before, reason, request.confirmedHighRisk());
        assertHighRiskMfaBound(before);
        SystemConfigItem after = repository.rollbackValue(SYSTEM_TENANT, normalizedKey, targetValue, actor, reason);
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.ROLLBACK,
            "system_config",
            normalizedKey,
            "回滚配置项：" + normalizedKey,
            snapshot(before, reason),
            snapshot(after, reason),
            null));
        applyRuntimeSideEffect(after);
        return SystemConfigItemResponse.from(after);
    }

    @Transactional
    public void seed(SystemConfigSeed seed, String actor) {
        repository.insertSeedIfAbsent(seed, actor);
    }

    public List<RuntimeFeatureFlag> runtimeFeatureFlags(RuntimeProperties properties) {
        return properties.getFeatureFlags().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> runtimeFeatureFlag(entry.getKey(), entry.getValue()))
            .toList();
    }

    public boolean runtimeFeatureFlagEnabled(RuntimeProperties properties, String key) {
        RuntimeProperties.FeatureFlag fallback = properties.getFeatureFlags().get(key);
        if (fallback == null) {
            return false;
        }
        return readFlagValue(configKey(key), fallback.isEnabled());
    }

    public boolean runtimeFeatureFlagEnabledForTenant(RuntimeProperties properties, String key, String tenantId) {
        RuntimeProperties.FeatureFlag fallback = properties.getFeatureFlags().get(key);
        if (fallback == null) {
            return false;
        }
        String configKey = configKey(key);
        RuntimeBooleanRead systemRead = readRuntimeBooleanConfig(configKey, fallback.isEnabled());
        if (tenantId == null || tenantId.isBlank() || SYSTEM_TENANT.equalsIgnoreCase(tenantId.trim())) {
            return systemRead.value();
        }
        try {
            SystemConfigItem tenantItem = repository.findActive(tenantId.trim(), configKey).orElse(null);
            if (tenantItem == null) {
                return systemRead.value();
            }
            Boolean tenantValue = parseBooleanOrNull(tenantItem.value());
            return tenantValue == null ? systemRead.value() : tenantValue;
        } catch (DataAccessException ignored) {
            return systemRead.value();
        }
    }

    public static String runtimeFeatureFlagConfigKey(String key) {
        return configKey(key);
    }

    public RuntimeBackupReadiness runtimeBackupReadiness(RuntimeProperties properties) {
        RuntimeProperties.Backup backup = properties.getBackup();
        RuntimeBooleanRead enabled = readRuntimeBooleanConfig(RUNTIME_BACKUP_PREFIX + "enabled", backup.isEnabled());
        RuntimeStringRead rpo = readRuntimeStringConfig(RUNTIME_BACKUP_PREFIX + "rpo", backup.getRpo());
        RuntimeStringRead rto = readRuntimeStringConfig(RUNTIME_BACKUP_PREFIX + "rto", backup.getRto());
        RuntimeStringRead backupScript = readRuntimeStringConfig(
            RUNTIME_BACKUP_PREFIX + "backup-script", backup.getBackupScript());
        RuntimeStringRead restoreScript = readRuntimeStringConfig(
            RUNTIME_BACKUP_PREFIX + "restore-script", backup.getRestoreScript());
        RuntimeStringRead checksumPolicy = readRuntimeStringConfig(
            RUNTIME_BACKUP_PREFIX + "checksum-policy", backup.getChecksumPolicy());
        return new RuntimeBackupReadiness(
            enabled.value(),
            rpo.value(),
            rto.value(),
            backupScript.value(),
            restoreScript.value(),
            checksumPolicy.value(),
            RuntimeBackupDrillEvidence.notAvailable(),
            aggregateSource(enabled.source(), rpo.source(), rto.source(), backupScript.source(),
                restoreScript.source(), checksumPolicy.source()),
            aggregateWarning(enabled.warning(), rpo.warning(), rto.warning(), backupScript.warning(),
                restoreScript.warning(), checksumPolicy.warning())
        );
    }

    public long runtimeJwtTtlSeconds(AuthJwtProperties properties) {
        return readRuntimeLongConfig(AUTH_JWT_TTL_SECONDS_KEY, properties.ttlSeconds()).value();
    }

    public AuthMode runtimeAuthMode() {
        // 配置缺失时 readRuntimeStringConfig 返回 PLATFORM；若库内值被绕过 UI 写坏，解析失败按 DELEGATED 失败关闭。
        return AuthMode.parse(readRuntimeStringConfig(AUTH_MODE_KEY, AuthMode.PLATFORM.name()).value(), AuthMode.DELEGATED);
    }

    public AuthPasswordPolicy runtimeAuthPasswordPolicy() {
        return new AuthPasswordPolicy(
            safeMinInt(readRuntimeLongConfig(AUTH_PASSWORD_PREFIX + "min-length",
                MIN_STRONG_PASSWORD_LENGTH).value(), MIN_STRONG_PASSWORD_LENGTH),
            readRuntimeBooleanConfig(AUTH_PASSWORD_PREFIX + "require-uppercase", true).value(),
            readRuntimeBooleanConfig(AUTH_PASSWORD_PREFIX + "require-lowercase", true).value(),
            readRuntimeBooleanConfig(AUTH_PASSWORD_PREFIX + "require-digit", true).value(),
            readRuntimeBooleanConfig(AUTH_PASSWORD_PREFIX + "require-symbol", true).value()
        );
    }

    public AuthPasswordHashAlgorithm runtimeAuthPasswordHashAlgorithm() {
        return AuthPasswordHashAlgorithm.parse(
            readRuntimeStringConfig(AUTH_PASSWORD_PREFIX + "hash-algorithm", AuthPasswordHashAlgorithm.BCRYPT.name())
                .value());
    }

    public long runtimePasswordResetTokenTtlSeconds() {
        return readRuntimeLongConfig(AUTH_PASSWORD_PREFIX + "reset-token-ttl-seconds", 900).value();
    }

    public AuthLoginPolicy runtimeAuthLoginPolicy() {
        return new AuthLoginPolicy(
            safeInt(readRuntimeLongConfig(AUTH_LOGIN_PREFIX + "max-failed-attempts", 5).value(), 5),
            readRuntimeLongConfig(AUTH_LOGIN_PREFIX + "lockout-seconds", 900).value(),
            safeInt(readRuntimeLongConfig(AUTH_LOGIN_PREFIX + "rate-limit-attempts", 10).value(), 10),
            readRuntimeLongConfig(AUTH_LOGIN_PREFIX + "rate-limit-window-seconds", 60).value()
        );
    }

    public AuthCookieProperties runtimeCookieProperties(AuthCookieProperties properties) {
        RuntimeStringRead name = readRuntimeStringConfig(AUTH_COOKIE_PREFIX + "name", properties.name());
        RuntimeBooleanRead secure = readRuntimeBooleanConfig(AUTH_COOKIE_PREFIX + "secure", properties.secure());
        RuntimeStringRead sameSite = readRuntimeStringConfig(AUTH_COOKIE_PREFIX + "same-site", properties.sameSite());
        RuntimeStringRead path = readRuntimeStringConfig(AUTH_COOKIE_PREFIX + "path", properties.path());
        RuntimeLongRead maxAge = readRuntimeLongConfig(
            AUTH_COOKIE_PREFIX + "max-age-seconds", properties.maxAgeSeconds());
        return new AuthCookieProperties(
            name.value(),
            secure.value(),
            sameSite.value(),
            path.value(),
            maxAge.value());
    }

    public AuthSessionProperties runtimeSessionProperties(AuthSessionProperties properties) {
        RuntimeLongRead idleTimeout = readRuntimeLongConfig(
            AUTH_SESSION_PREFIX + "idle-timeout-seconds", properties.idleTimeoutSeconds());
        RuntimeLongRead warning = readRuntimeLongConfig(
            AUTH_SESSION_PREFIX + "warning-seconds", properties.warningSeconds());
        RuntimeLongRead maxDuration = readRuntimeLongConfig(
            AUTH_SESSION_PREFIX + "max-duration-seconds", properties.maxDurationSeconds());
        return new AuthSessionProperties(idleTimeout.value(), warning.value(), maxDuration.value());
    }

    public void applyRuntimeLogLevels() {
        repository.listActive(SYSTEM_TENANT, LOGGING_LEVEL_PREFIX).forEach(logLevelManager::apply);
    }

    public String runtimeAuditFallbackPath(AuditFallbackProperties properties) {
        return readRuntimeStringConfig(AUDIT_FALLBACK_PATH_KEY, properties.pathOrDefault()).value();
    }

    public long runtimeClinicalEventWorkerPollIntervalMs(ClinicalEventWorkerSettings properties) {
        return readRuntimeLongConfig(
            CLINICAL_EVENT_WORKER_POLL_INTERVAL_MS_KEY,
            properties.workerPollIntervalMs()).value();
    }

    public long runtimeClinicalEventSyncTimeoutMs(ClinicalEventWorkerSettings properties) {
        long fallbackMs = Math.max(1L, properties.syncTimeout().toMillis());
        return readRuntimeLongConfig(CLINICAL_EVENT_SYNC_TIMEOUT_MS_KEY, fallbackMs).value();
    }

    public long runtimeRealtimeCdsDefaultTimeoutMs(RealtimeCdsSettings properties) {
        return readRuntimeLongConfig(
            REALTIME_CDS_DEFAULT_TIMEOUT_MS_KEY,
            durationMs(properties == null ? null : properties.defaultTimeout(), Duration.ofSeconds(2))).value();
    }

    public long runtimeRealtimeCdsOrderSignTimeoutMs(RealtimeCdsSettings properties) {
        return readRuntimeLongConfig(
            REALTIME_CDS_ORDER_SIGN_TIMEOUT_MS_KEY,
            durationMs(properties == null ? null : properties.orderSignTimeout(), Duration.ofSeconds(1))).value();
    }

    public long runtimeIntegrationHealthProbeIntervalMs(IntegrationHealthProbeSettings properties) {
        return readRuntimeLongConfig(
            INTEGRATION_HEALTH_PROBE_INTERVAL_MS_KEY,
            properties.healthProbeIntervalMs()).value();
    }

    public long runtimeKnowledgeRetirementIntervalMs() {
        return readRuntimeLongConfig(
            KNOWLEDGE_RETIREMENT_INTERVAL_MS_KEY,
            DEFAULT_KNOWLEDGE_RETIREMENT_INTERVAL_MS).value();
    }

    public long runtimeKnowledgeAcquisitionScheduleIntervalMs() {
        return readRuntimeLongConfig(
            KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS_KEY,
            DEFAULT_KNOWLEDGE_ACQUISITION_SCHEDULE_INTERVAL_MS).value();
    }

    public String runtimeKnowledgeLiteratureMaterialRootUri() {
        return readRuntimeStringConfig(
            KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY,
            DEFAULT_KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI).value();
    }

    public String runtimeDeploymentForm() {
        return readRuntimeStringConfig(DEPLOYMENT_FORM_KEY, DEFAULT_DEPLOYMENT_FORM).value();
    }

    private static long durationMs(Duration configured, Duration fallback) {
        Duration duration = configured == null || configured.isZero() || configured.isNegative()
            ? fallback
            : configured;
        return Math.max(1L, duration.toMillis());
    }

    private RuntimeFeatureFlag runtimeFeatureFlag(String key, RuntimeProperties.FeatureFlag fallback) {
        RuntimeBooleanRead read = readRuntimeBooleanConfig(configKey(key), fallback.isEnabled());
        SystemConfigItem config = read.item();
        return new RuntimeFeatureFlag(
            key,
            valueOrFallback(config == null ? null : config.displayName(), fallback.getDisplayName()),
            read.value(),
            valueOrFallback(config == null ? null : config.risk(), fallback.getRisk()),
            valueOrFallback(config == null ? null : config.owner(), fallback.getOwner()),
            valueOrFallback(config == null ? null : config.description(), fallback.getDescription()),
            read.source(),
            read.warning());
    }

    private boolean readFlagValue(String configKey, boolean fallback) {
        return readRuntimeBooleanConfig(configKey, fallback).value();
    }

    private RuntimeStringRead readRuntimeStringConfig(String configKey, String fallback) {
        try {
            SystemConfigItem item = repository.findActive(SYSTEM_TENANT, configKey).orElse(null);
            if (item == null || item.value() == null || item.value().isBlank()) {
                return new RuntimeStringRead(
                    fallback,
                    SAFE_DEFAULT_SOURCE,
                    "配置中心缺少运行配置，已使用启动安全默认。");
            }
            return new RuntimeStringRead(item.value(), valueOrFallback(item.source(), "CONFIG_CENTER"), null);
        } catch (DataAccessException ignored) {
            return new RuntimeStringRead(
                fallback,
                SAFE_DEFAULT_SOURCE,
                "配置中心读取失败，已使用启动安全默认。");
        }
    }

    private RuntimeLongRead readRuntimeLongConfig(String configKey, long fallback) {
        try {
            SystemConfigItem item = repository.findActive(SYSTEM_TENANT, configKey).orElse(null);
            if (item == null || item.value() == null || item.value().isBlank()) {
                return new RuntimeLongRead(
                    fallback,
                    SAFE_DEFAULT_SOURCE,
                    "配置中心缺少运行配置，已使用启动安全默认。");
            }
            try {
                long parsed = Long.parseLong(item.value().trim());
                if (parsed <= 0) {
                    throw new NumberFormatException("non-positive");
                }
                return new RuntimeLongRead(parsed, valueOrFallback(item.source(), "CONFIG_CENTER"), null);
            } catch (NumberFormatException ignored) {
                return new RuntimeLongRead(
                    fallback,
                    SAFE_DEFAULT_SOURCE,
                    "配置中心数值非法，已使用启动安全默认。");
            }
        } catch (DataAccessException ignored) {
            return new RuntimeLongRead(
                fallback,
                SAFE_DEFAULT_SOURCE,
                "配置中心读取失败，已使用启动安全默认。");
        }
    }

    private static String configKey(String runtimeFlagKey) {
        return RUNTIME_FLAG_PREFIX + runtimeFlagKey + RUNTIME_FLAG_SUFFIX;
    }

    private static void validateValue(SystemConfigItem item, String value) {
        String valueType = item.valueType() == null ? "STRING" : item.valueType().toUpperCase(Locale.ROOT);
        switch (valueType) {
            case "BOOLEAN" -> {
                String normalized = value.toLowerCase(Locale.ROOT);
                if (!"true".equals(normalized) && !"false".equals(normalized)) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "布尔配置仅允许 true 或 false");
                }
            }
            default -> {
            }
        }
        validateRuntimePolicyValue(item.key(), value);
    }

    private static void validateRuntimePolicyValue(String key, String value) {
        if (AUTH_JWT_TTL_SECONDS_KEY.equals(key)
            || CLINICAL_EVENT_SYNC_TIMEOUT_MS_KEY.equals(key)
            || CLINICAL_EVENT_WORKER_POLL_INTERVAL_MS_KEY.equals(key)
            || REALTIME_CDS_DEFAULT_TIMEOUT_MS_KEY.equals(key)
            || REALTIME_CDS_ORDER_SIGN_TIMEOUT_MS_KEY.equals(key)
            || KNOWLEDGE_RETIREMENT_INTERVAL_MS_KEY.equals(key)
            || (key != null && key.equals(AUTH_COOKIE_PREFIX + "max-age-seconds"))
            || (key != null && key.startsWith(AUTH_SESSION_PREFIX))) {
            validatePositiveLong(value);
            return;
        }
        if (key != null && key.equals(AUTH_COOKIE_PREFIX + "same-site")) {
            validateOption(value, Set.of("strict", "lax", "none"), "Cookie SameSite 仅允许 Strict/Lax/None");
            return;
        }
        if (AUTH_MODE_KEY.equals(key)) {
            validateOption(value, Set.of("platform", "delegated", "both"),
                "认证模式仅允许 PLATFORM/DELEGATED/BOTH");
            return;
        }
        if (KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY.equals(key)) {
            validateKnowledgeLiteratureMaterialRootUri(value);
            return;
        }
        if (key != null && key.startsWith(LOGGING_LEVEL_PREFIX)) {
            RuntimeLogLevelManager.parseLogLevel(value);
            return;
        }
        if (key != null && key.startsWith(AUTH_PASSWORD_PREFIX)) {
            if (key.endsWith(".min-length")) {
                validateMinLong(value, MIN_STRONG_PASSWORD_LENGTH, "密码最小长度不能低于 12 位");
            } else if (key.endsWith(".hash-algorithm")) {
                validateOption(value, Set.of("bcrypt", "sm3"), "口令哈希算法仅允许 BCRYPT/SM3");
            } else if (key.endsWith(".reset-token-ttl-seconds")) {
                validatePositiveLong(value);
            } else {
                validateBooleanText(value);
            }
            return;
        }
        if (key != null && key.startsWith(AUTH_LOGIN_PREFIX)) {
            validatePositiveLong(value);
        }
    }

    private static void validateKnowledgeLiteratureMaterialRootUri(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        boolean hasUriScheme = normalized.matches("[a-z][a-z0-9+.-]*://.+");
        String scheme = hasUriScheme ? normalized.substring(0, normalized.indexOf("://")) : "";
        boolean usesTmp = normalized.contains("/tmp/")
            || normalized.endsWith("/tmp")
            || normalized.contains("://tmp/")
            || normalized.startsWith("tmp://");
        if (!hasUriScheme
            || FORBIDDEN_KNOWLEDGE_LITERATURE_URI_SCHEMES.contains(scheme)
            || !normalized.endsWith("/")
            || !normalized.contains("/platform-knowledge/t-1/literature-materials/")
            || usesTmp) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "平台知识文献资料库根地址必须使用正式受管资料库 URI，保留 /platform-knowledge/t-1/literature-materials/ 结构，且不能指向 tmp、相对路径或非加密 HTTP");
        }
        if ("file".equals(scheme)) {
            try {
                Path path = Path.of(URI.create(value.trim()));
                if (!path.isAbsolute()) {
                    throw new IllegalArgumentException("relative");
                }
            } catch (IllegalArgumentException exception) {
                throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "本地文献资料库根地址必须使用 file:/// 开头的绝对受管路径");
            }
        }
    }

    private static void validatePositiveLong(String value) {
        try {
            if (Long.parseLong(value.trim()) <= 0) {
                throw new NumberFormatException("non-positive");
            }
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "数值配置必须是正整数");
        }
    }

    private static void validateBooleanText(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "布尔配置仅允许 true 或 false");
        }
    }

    private static void validateOption(String value, Set<String> allowed, String message) {
        if (!allowed.contains(value.trim().toLowerCase(Locale.ROOT))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        Boolean parsed = parseBooleanOrNull(value);
        return parsed == null ? fallback : parsed;
    }

    private static Boolean parseBooleanOrNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        return null;
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "配置键不能为空");
        }
        return key.trim();
    }

    private static String normalizeTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId.trim();
    }

    private static String normalizeValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "配置值不能为空");
        }
        return value.trim();
    }

    private static String normalizeRollbackValue(String key, String value) {
        if (KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY.equals(key)
            && (value == null || value.isBlank())) {
            return DEFAULT_KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI;
        }
        return normalizeValue(value);
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "配置变更原因不能为空");
        }
        return reason.trim();
    }

    private static Map<String, Object> snapshot(SystemConfigItem item, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", item.key());
        result.put("value", item.value());
        result.put("version", item.version());
        result.put("risk", item.risk());
        if (reason != null && !reason.isBlank()) {
            result.put("reason", reason);
        }
        return result;
    }

    private void assertProtectedRuntimeDisableAllowed(SystemConfigItem item, String value, String reason) {
        if (!PROTECTED_RUNTIME_DISABLE_KEYS.contains(item.key())) {
            return;
        }
        if (!parseBoolean(item.value(), true) || parseBoolean(value, true)) {
            return;
        }
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.PERMISSION_CHANGE,
            "system_config",
            item.key(),
            "拒绝关闭高危运行配置：" + item.key(),
            snapshot(item, reason),
            Map.of("key", item.key(), "value", value, "reason", reason == null ? "" : reason),
            null));
        throw new ApiException(ErrorCode.ENG_CONFIG_001);
    }

    private void assertHighRiskChangeConfirmed(SystemConfigItem item, String reason, Boolean confirmedHighRisk) {
        if (!isHighRisk(item)) {
            return;
        }
        if (Boolean.TRUE.equals(confirmedHighRisk) && reason != null && !reason.isBlank()) {
            return;
        }
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.PERMISSION_CHANGE,
            "system_config",
            item.key(),
            "拒绝未确认高危配置变更：" + item.key(),
            snapshot(item, reason),
            Map.of("key", item.key(), "reason", reason == null ? "" : reason),
            null));
        throw new ApiException(ErrorCode.ENG_CONFIG_002);
    }

    private static boolean isHighRisk(SystemConfigItem item) {
        return item.protectedConfig() || "HIGH".equalsIgnoreCase(item.risk());
    }

    private void assertHighRiskMfaBound(SystemConfigItem item) {
        if (isHighRisk(item)) {
            highRiskChangeGuard.assertHighRiskAllowed("system_config", item.key());
        }
    }

    private RuntimeBooleanRead readRuntimeBooleanConfig(String configKey, boolean fallback) {
        try {
            SystemConfigItem item = repository.findActive(SYSTEM_TENANT, configKey).orElse(null);
            if (item == null) {
                return new RuntimeBooleanRead(
                    null,
                    fallback,
                    SAFE_DEFAULT_SOURCE,
                    "配置中心缺少运行配置，已使用启动安全默认。");
            }
            Boolean parsed = parseBooleanOrNull(item.value());
            if (parsed == null) {
                return new RuntimeBooleanRead(
                    item,
                    fallback,
                    SAFE_DEFAULT_SOURCE,
                    "配置中心布尔值非法，已使用启动安全默认。");
            }
            return new RuntimeBooleanRead(item, parsed, valueOrFallback(item.source(), "CONFIG_CENTER"), null);
        } catch (DataAccessException ignored) {
            return new RuntimeBooleanRead(
                null,
                fallback,
                SAFE_DEFAULT_SOURCE,
                "配置中心读取失败，已使用启动安全默认。");
        }
    }

    private static String aggregateSource(String... sources) {
        for (String source : sources) {
            if (SAFE_DEFAULT_SOURCE.equals(source)) {
                return SAFE_DEFAULT_SOURCE;
            }
        }
        for (String source : sources) {
            if (source != null && !source.isBlank()) {
                return source;
            }
        }
        return "CONFIG_CENTER";
    }

    private static String aggregateWarning(String... warnings) {
        return java.util.Arrays.stream(warnings)
            .filter(warning -> warning != null && !warning.isBlank())
            .distinct()
            .reduce((left, right) -> left + " " + right)
            .orElse(null);
    }

    private static String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static SystemConfigItem inheritedTenantItem(String tenantId, SystemConfigItem systemItem) {
        return new SystemConfigItem(
            tenantId,
            systemItem.key(),
            systemItem.value(),
            systemItem.valueType(),
            systemItem.displayName(),
            systemItem.risk(),
            systemItem.owner(),
            systemItem.description(),
            "SYSTEM_INHERITED",
            systemItem.protectedConfig(),
            systemItem.active(),
            systemItem.version(),
            systemItem.updatedAt());
    }

    private static int safeInt(long value, int fallback) {
        if (value <= 0 || value > Integer.MAX_VALUE) {
            return fallback;
        }
        return (int) value;
    }

    private static int safeMinInt(long value, int minValue) {
        if (value < minValue || value > Integer.MAX_VALUE) {
            return minValue;
        }
        return (int) value;
    }

    private static void validateMinLong(String value, long minValue, String message) {
        validatePositiveLong(value);
        if (Long.parseLong(value.trim()) < minValue) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    private void applyRuntimeSideEffect(SystemConfigItem item) {
        if (logLevelManager.supports(item.key())) {
            logLevelManager.apply(item);
        }
    }

    private record RuntimeBooleanRead(
        SystemConfigItem item,
        boolean value,
        String source,
        String warning
    ) {
    }

    private record RuntimeStringRead(
        String value,
        String source,
        String warning
    ) {
    }

    private record RuntimeLongRead(
        long value,
        String source,
        String warning
    ) {
    }

    public static String currentActor(String authenticationName) {
        return RequestContext.currentUserId()
            .orElse(authenticationName == null || authenticationName.isBlank() ? "system" : authenticationName);
    }
}
