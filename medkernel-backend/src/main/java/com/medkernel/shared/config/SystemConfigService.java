package com.medkernel.shared.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.context.ClinicalEventProperties;
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
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeFeatureFlag;
import com.medkernel.shared.runtime.RuntimeProperties;
import com.medkernel.shared.security.AuthCookieProperties;
import com.medkernel.shared.security.AuthJwtProperties;

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
    public static final String AUTH_COOKIE_PREFIX = "medkernel.auth.cookie.";
    public static final String LOGGING_LEVEL_PREFIX = "medkernel.logging.level.";
    public static final String AUDIT_FALLBACK_PATH_KEY = "medkernel.audit.fallback.path";
    public static final String CLINICAL_EVENT_WORKER_POLL_INTERVAL_MS_KEY =
        "medkernel.events.worker.poll-interval-ms";
    private static final String SAFE_DEFAULT_SOURCE = "SAFE_DEFAULT";
    private static final Set<String> PROTECTED_RUNTIME_DISABLE_KEYS = Set.of(
        RUNTIME_FLAG_PREFIX + "domestic-crypto" + RUNTIME_FLAG_SUFFIX);

    private final SystemConfigRepository repository;
    private final AuditSafetyGuard auditSafetyGuard;
    private final AuditRecorder auditRecorder;
    private final RuntimeLogLevelManager logLevelManager;

    public SystemConfigService(SystemConfigRepository repository,
                               AuditSafetyGuard auditSafetyGuard,
                               AuditRecorder auditRecorder,
                               RuntimeLogLevelManager logLevelManager) {
        this.repository = repository;
        this.auditSafetyGuard = auditSafetyGuard;
        this.auditRecorder = auditRecorder;
        this.logLevelManager = logLevelManager;
    }

    public List<SystemConfigItemResponse> list(String prefix) {
        return repository.listActive(SYSTEM_TENANT, prefix).stream()
            .map(SystemConfigItemResponse::from)
            .toList();
    }

    @Transactional
    public SystemConfigItemResponse update(String key, SystemConfigUpdateRequest request, String actor) {
        String normalizedKey = normalizeKey(key);
        String value = normalizeValue(request.value());
        SystemConfigItem before = repository.findActive(SYSTEM_TENANT, normalizedKey)
            .orElseThrow(() -> ApiException.notFound("配置项 " + normalizedKey));
        validateValue(before, value);
        auditSafetyGuard.assertChangeAllowed(
            new AuditConfigChangeCommand(normalizedKey, before.value(), value, request.reason()));
        assertProtectedRuntimeDisableAllowed(before, value, request.reason());
        assertHighRiskChangeConfirmed(before, request.reason(), request.confirmedHighRisk());
        SystemConfigItem after = repository.updateValue(
            SYSTEM_TENANT, normalizedKey, value, actor, request.reason(), request.expectedVersion());
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.UPDATE,
            "system_config",
            normalizedKey,
            "更新配置项：" + normalizedKey,
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
        String targetValue = normalizeValue(latest.beforeValue());
        validateValue(before, targetValue);
        auditSafetyGuard.assertChangeAllowed(
            new AuditConfigChangeCommand(normalizedKey, before.value(), targetValue, reason));
        assertProtectedRuntimeDisableAllowed(before, targetValue, reason);
        assertHighRiskChangeConfirmed(before, reason, request.confirmedHighRisk());
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
            aggregateSource(enabled.source(), rpo.source(), rto.source(), backupScript.source(),
                restoreScript.source(), checksumPolicy.source()),
            aggregateWarning(enabled.warning(), rpo.warning(), rto.warning(), backupScript.warning(),
                restoreScript.warning(), checksumPolicy.warning())
        );
    }

    public long runtimeJwtTtlSeconds(AuthJwtProperties properties) {
        return readRuntimeLongConfig(AUTH_JWT_TTL_SECONDS_KEY, properties.ttlSeconds()).value();
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

    public void applyRuntimeLogLevels() {
        repository.listActive(SYSTEM_TENANT, LOGGING_LEVEL_PREFIX).forEach(logLevelManager::apply);
    }

    public String runtimeAuditFallbackPath(AuditFallbackProperties properties) {
        return readRuntimeStringConfig(AUDIT_FALLBACK_PATH_KEY, properties.pathOrDefault()).value();
    }

    public long runtimeClinicalEventWorkerPollIntervalMs(ClinicalEventProperties properties) {
        return readRuntimeLongConfig(
            CLINICAL_EVENT_WORKER_POLL_INTERVAL_MS_KEY,
            properties.workerPollIntervalMs()).value();
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
            || CLINICAL_EVENT_WORKER_POLL_INTERVAL_MS_KEY.equals(key)
            || (key != null && key.equals(AUTH_COOKIE_PREFIX + "max-age-seconds"))) {
            validatePositiveLong(value);
            return;
        }
        if (key != null && key.equals(AUTH_COOKIE_PREFIX + "same-site")) {
            validateOption(value, Set.of("strict", "lax", "none"), "Cookie SameSite 仅允许 Strict/Lax/None");
            return;
        }
        if (key != null && key.startsWith(LOGGING_LEVEL_PREFIX)) {
            RuntimeLogLevelManager.parseLogLevel(value);
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

    private static String normalizeValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "配置值不能为空");
        }
        return value.trim();
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
