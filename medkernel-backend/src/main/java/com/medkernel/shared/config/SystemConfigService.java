package com.medkernel.shared.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditConfigChangeCommand;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.AuditSafetyGuard;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupReadiness;
import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeFeatureFlag;
import com.medkernel.shared.runtime.RuntimeProperties;

/**
 * 配置中心应用服务，负责配置读写、变更历史和运行底座热生效。
 */
@Service
public class SystemConfigService {

    static final String SYSTEM_TENANT = "SYSTEM";
    static final String RUNTIME_FLAG_PREFIX = "medkernel.runtime.feature-flags.";
    static final String RUNTIME_FLAG_SUFFIX = ".enabled";
    static final String RUNTIME_BACKUP_PREFIX = "medkernel.runtime.backup.";
    private static final Set<String> PROTECTED_RUNTIME_DISABLE_KEYS = Set.of(
        RUNTIME_FLAG_PREFIX + "domestic-crypto" + RUNTIME_FLAG_SUFFIX);

    private final SystemConfigRepository repository;
    private final AuditSafetyGuard auditSafetyGuard;
    private final AuditRecorder auditRecorder;

    public SystemConfigService(SystemConfigRepository repository,
                               AuditSafetyGuard auditSafetyGuard,
                               AuditRecorder auditRecorder) {
        this.repository = repository;
        this.auditSafetyGuard = auditSafetyGuard;
        this.auditRecorder = auditRecorder;
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
        SystemConfigItem after = repository.updateValue(SYSTEM_TENANT, normalizedKey, value, actor, request.reason());
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.UPDATE,
            "system_config",
            normalizedKey,
            "更新配置项：" + normalizedKey,
            snapshot(before, request.reason()),
            snapshot(after, request.reason()),
            null));
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
        return new RuntimeBackupReadiness(
            readBooleanValue(RUNTIME_BACKUP_PREFIX + "enabled", backup.isEnabled()),
            readStringValue(RUNTIME_BACKUP_PREFIX + "rpo", backup.getRpo()),
            readStringValue(RUNTIME_BACKUP_PREFIX + "rto", backup.getRto()),
            readStringValue(RUNTIME_BACKUP_PREFIX + "backup-script", backup.getBackupScript()),
            readStringValue(RUNTIME_BACKUP_PREFIX + "restore-script", backup.getRestoreScript()),
            readStringValue(RUNTIME_BACKUP_PREFIX + "checksum-policy", backup.getChecksumPolicy())
        );
    }

    private RuntimeFeatureFlag runtimeFeatureFlag(String key, RuntimeProperties.FeatureFlag fallback) {
        SystemConfigItem config = repository.findActive(SYSTEM_TENANT, configKey(key)).orElse(null);
        boolean enabled = config == null ? fallback.isEnabled() : parseBoolean(config.value(), fallback.isEnabled());
        return new RuntimeFeatureFlag(
            key,
            valueOrFallback(config == null ? null : config.displayName(), fallback.getDisplayName()),
            enabled,
            valueOrFallback(config == null ? null : config.risk(), fallback.getRisk()),
            valueOrFallback(config == null ? null : config.owner(), fallback.getOwner()),
            valueOrFallback(config == null ? null : config.description(), fallback.getDescription()));
    }

    private boolean readFlagValue(String configKey, boolean fallback) {
        return repository.findActive(SYSTEM_TENANT, configKey)
            .map(item -> parseBoolean(item.value(), fallback))
            .orElse(fallback);
    }

    private boolean readBooleanValue(String configKey, boolean fallback) {
        return repository.findActive(SYSTEM_TENANT, configKey)
            .map(item -> parseBoolean(item.value(), fallback))
            .orElse(fallback);
    }

    private String readStringValue(String configKey, String fallback) {
        return repository.findActive(SYSTEM_TENANT, configKey)
            .map(SystemConfigItem::value)
            .filter(value -> !value.isBlank())
            .orElse(fallback);
    }

    private static String configKey(String runtimeFlagKey) {
        return RUNTIME_FLAG_PREFIX + runtimeFlagKey + RUNTIME_FLAG_SUFFIX;
    }

    private static void validateValue(SystemConfigItem item, String value) {
        if (!"BOOLEAN".equalsIgnoreCase(item.valueType())) {
            return;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "布尔配置仅允许 true 或 false");
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        return fallback;
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

    private static String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String currentActor(String authenticationName) {
        return RequestContext.currentUserId()
            .orElse(authenticationName == null || authenticationName.isBlank() ? "system" : authenticationName);
    }
}
