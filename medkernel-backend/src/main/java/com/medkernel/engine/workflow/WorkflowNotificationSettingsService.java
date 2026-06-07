package com.medkernel.engine.workflow;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.experience.UserPreference;
import com.medkernel.engine.experience.UserPreferenceRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.config.SystemConfigItemResponse;
import com.medkernel.shared.config.SystemConfigSeed;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.config.SystemConfigUpdateRequest;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知偏好与免打扰策略服务。
 *
 * <p>租户默认策略由配置中心托管，个人偏好按用户覆盖；外部通道只登记真实出站补偿，
 * 不声明短信、移动推送等未连接通道已完成投递。
 */
@Service
public class WorkflowNotificationSettingsService {

    public static final String SYSTEM_DEFAULTS_KEY = "medkernel.notification.defaults";
    private static final String ACTIVE = "ACTIVE";
    private static final String PREF_KEY = "notification.settings";
    private static final String DEFAULT_QUIET_START = "22:00";
    private static final String DEFAULT_QUIET_END = "07:00";
    private static final Set<WorkflowNotificationLevel> SAFETY_BYPASS_LEVELS =
        Set.of(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH);
    private static final Set<WorkflowNotificationType> MANDATORY_TYPES =
        Set.of(WorkflowNotificationType.SAFETY);

    private final UserPreferenceRepository repository;
    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final AuditRecorder auditRecorder;

    public WorkflowNotificationSettingsService(
            UserPreferenceRepository repository,
            ObjectMapper objectMapper,
            SystemConfigService systemConfigService,
            AuditRecorder auditRecorder) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 读取当前用户通知偏好。
     */
    @Transactional(readOnly = true)
    public WorkflowNotificationSettingsResponse getSettings() {
        String tenantId = requireTenantId();
        String userId = requireUserId();
        return getSettingsForUser(tenantId, userId);
    }

    /**
     * 读取指定接收人的通知偏好，用于协同通知外发补偿判断。
     */
    @Transactional(readOnly = true)
    public WorkflowNotificationSettingsResponse getSettingsForUser(String tenantId, String userId) {
        String normalizedTenantId = requireText(tenantId, "租户标识");
        String normalizedUserId = requireText(userId, "接收人");
        SystemSettings systemSettings = systemSettings(normalizedTenantId);
        return repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                normalizedTenantId, normalizedUserId, PREF_KEY, ACTIVE)
            .map(preference -> responseFromPreference(preference, systemSettings.version()))
            .orElseGet(() -> response(
                systemSettings.payload(),
                WorkflowNotificationSettingsSource.SYSTEM_DEFAULT,
                0,
                systemSettings.version(),
                systemSettings.updatedAt(),
                null));
    }

    /**
     * 读取当前租户的系统通知默认策略。
     */
    @Transactional
    public WorkflowNotificationSettingsResponse getSystemSettings() {
        SystemSettings systemSettings = systemSettings(requireTenantId());
        return response(
            systemSettings.payload(),
            WorkflowNotificationSettingsSource.SYSTEM_DEFAULT,
            0,
            systemSettings.version(),
            systemSettings.updatedAt(),
            null);
    }

    /**
     * 保存当前用户通知偏好。
     */
    @Transactional
    public WorkflowNotificationSettingsResponse saveSettings(WorkflowNotificationSettingsRequest request) {
        SettingsPayload payload = normalize(request);
        String tenantId = requireTenantId();
        String userId = requireUserId();
        SystemSettings systemSettings = systemSettings(tenantId);
        Instant now = Instant.now();
        String value = writePayload(payload);
        UserPreference existing = repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                tenantId, userId, PREF_KEY, ACTIVE)
            .orElse(null);

        UserPreference preference = existing == null
            ? new UserPreference(
                "up-" + UUID.randomUUID(),
                tenantId,
                userId,
                PREF_KEY,
                value,
                1,
                ACTIVE,
                now,
                userId,
                now,
                userId)
            : new UserPreference(
                existing.userPrefId(),
                existing.tenantId(),
                existing.userId(),
                existing.prefKey(),
                value,
                existing.version() + 1,
                ACTIVE,
                existing.createdAt(),
                existing.createdBy(),
                now,
                userId);

        UserPreference saved = repository.save(preference);
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.UPDATE,
            "notification_settings",
            userId,
            "更新个人通知设置",
            existing == null ? Map.of() : Map.of("version", existing.version()),
            auditSnapshot(payload),
            null));
        return response(
            payload,
            WorkflowNotificationSettingsSource.PERSONAL,
            saved.version(),
            systemSettings.version(),
            saved.updatedAt(),
            saved.updatedBy());
    }

    /**
     * 更新当前租户的系统通知默认策略。
     */
    @Transactional
    public WorkflowNotificationSettingsResponse saveSystemSettings(
            WorkflowNotificationSystemSettingsRequest request) {
        if (request == null || request.settings() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "租户通知默认策略不能为空");
        }
        SettingsPayload payload = normalize(request.settings());
        String tenantId = requireTenantId();
        String actor = requireUserId();
        SystemConfigItemResponse saved = systemConfigService.updateTenant(
            tenantId,
            SYSTEM_DEFAULTS_KEY,
            new SystemConfigUpdateRequest(
                writePayload(payload),
                request.reason(),
                request.expectedVersion(),
                false),
            actor);
        SettingsPayload savedPayload = readPayload(saved.value(), "租户通知默认策略已损坏");
        return response(
            savedPayload,
            WorkflowNotificationSettingsSource.SYSTEM_DEFAULT,
            0,
            saved.version(),
            saved.updatedAt(),
            actor);
    }

    boolean isMutedByQuietHours(
            WorkflowNotificationLevel level,
            WorkflowNotificationSettingsResponse settings,
            LocalTime now) {
        if (settings == null || !settings.quietHoursEnabled()) {
            return false;
        }
        if (settings.quietBypassLevels().contains(level)) {
            return false;
        }
        return quietActive(settings.quietStart(), settings.quietEnd(), now);
    }

    boolean isSubscribed(
            WorkflowNotificationSourceType sourceType,
            WorkflowNotificationLevel level,
            WorkflowNotificationSettingsResponse settings) {
        if (settings == null) {
            return false;
        }
        if (level == WorkflowNotificationLevel.CRITICAL || level == WorkflowNotificationLevel.HIGH) {
            return true;
        }
        WorkflowNotificationType type = switch (sourceType) {
            case SAFETY_REVIEW -> WorkflowNotificationType.SAFETY;
            case FOLLOWUP_EVENT -> WorkflowNotificationType.FOLLOWUP;
            case WORKFLOW_TODO -> WorkflowNotificationType.WORKFLOW;
            case SYNC_EVENT -> WorkflowNotificationType.SYNC;
        };
        return settings.subscribedTypes().contains(type);
    }

    private WorkflowNotificationSettingsResponse responseFromPreference(
            UserPreference preference,
            long systemVersion) {
        return response(
            readPayload(preference),
            WorkflowNotificationSettingsSource.PERSONAL,
            preference.version(),
            systemVersion,
            preference.updatedAt(),
            preference.updatedBy());
    }

    private WorkflowNotificationSettingsResponse response(
            SettingsPayload payload,
            WorkflowNotificationSettingsSource source,
            long version,
            long systemVersion,
            Instant updatedAt,
            String updatedBy) {
        return new WorkflowNotificationSettingsResponse(
            payload.inAppEnabled(),
            payload.smsEnabled(),
            payload.emailEnabled(),
            payload.pushEnabled(),
            payload.webhookEnabled(),
            payload.inHospitalMessageEnabled(),
            payload.quietHoursEnabled(),
            payload.quietStart(),
            payload.quietEnd(),
            payload.quietBypassLevels(),
            payload.subscribedTypes(),
            MANDATORY_TYPES,
            source,
            payload.quietHoursEnabled() && quietActive(payload.quietStart(), payload.quietEnd(), LocalTime.now()),
            version,
            systemVersion,
            updatedAt,
            updatedBy);
    }

    private SettingsPayload normalize(WorkflowNotificationSettingsRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "通知设置不能为空");
        }
        String quietStart = normalizeTime(request.quietStart(), DEFAULT_QUIET_START);
        String quietEnd = normalizeTime(request.quietEnd(), DEFAULT_QUIET_END);
        return new SettingsPayload(
            defaultTrue(request.inAppEnabled()),
            defaultFalse(request.smsEnabled()),
            defaultFalse(request.emailEnabled()),
            defaultFalse(request.pushEnabled()),
            defaultFalse(request.webhookEnabled()),
            defaultFalse(request.inHospitalMessageEnabled()),
            defaultFalse(request.quietHoursEnabled()),
            quietStart,
            quietEnd,
            normalizeBypassLevels(request.quietBypassLevels()),
            normalizeSubscribedTypes(request.subscribedTypes()));
    }

    private SettingsPayload readPayload(UserPreference preference) {
        return readPayload(preference.prefValue(), "通知设置偏好已损坏，请重新保存");
    }

    private SettingsPayload readPayload(String value, String errorMessage) {
        try {
            return normalize(objectMapper.readValue(value, SettingsPayload.class));
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.CONFLICT, errorMessage);
        }
    }

    private SettingsPayload normalize(SettingsPayload payload) {
        if (payload == null) {
            return defaultPayload();
        }
        return new SettingsPayload(
            payload.inAppEnabled(),
            payload.smsEnabled(),
            payload.emailEnabled(),
            payload.pushEnabled(),
            payload.webhookEnabled(),
            payload.inHospitalMessageEnabled(),
            payload.quietHoursEnabled(),
            normalizeTime(payload.quietStart(), DEFAULT_QUIET_START),
            normalizeTime(payload.quietEnd(), DEFAULT_QUIET_END),
            normalizeBypassLevels(payload.quietBypassLevels()),
            normalizeSubscribedTypes(payload.subscribedTypes()));
    }

    private String writePayload(SettingsPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "通知设置序列化失败");
        }
    }

    private static SettingsPayload defaultPayload() {
        return new SettingsPayload(
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            DEFAULT_QUIET_START,
            DEFAULT_QUIET_END,
            normalizeBypassLevels(null),
            normalizeSubscribedTypes(null));
    }

    private static Set<WorkflowNotificationLevel> normalizeBypassLevels(Set<WorkflowNotificationLevel> levels) {
        LinkedHashSet<WorkflowNotificationLevel> normalized = new LinkedHashSet<>();
        normalized.add(WorkflowNotificationLevel.CRITICAL);
        normalized.add(WorkflowNotificationLevel.HIGH);
        if (levels != null) {
            normalized.addAll(levels);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<WorkflowNotificationType> normalizeSubscribedTypes(
            Set<WorkflowNotificationType> types) {
        LinkedHashSet<WorkflowNotificationType> normalized = new LinkedHashSet<>();
        normalized.add(WorkflowNotificationType.SAFETY);
        if (types == null) {
            normalized.add(WorkflowNotificationType.FOLLOWUP);
            normalized.add(WorkflowNotificationType.WORKFLOW);
            normalized.add(WorkflowNotificationType.SYNC);
        } else {
            for (WorkflowNotificationType type : WorkflowNotificationType.values()) {
                if (types.contains(type)) {
                    normalized.add(type);
                }
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private SystemSettings systemSettings(String tenantId) {
        SettingsPayload defaults = defaultPayload();
        Instant now = Instant.now();
        SystemConfigItemResponse config = systemConfigService.getOrSeedTenantConfig(
            tenantId,
            SYSTEM_DEFAULTS_KEY,
            new SystemConfigSeed(
                tenantId,
                SYSTEM_DEFAULTS_KEY,
                writePayload(defaults),
                "JSON",
                "租户通知默认策略",
                "MEDIUM",
                "医院管理员",
                "租户通知渠道、订阅类型和免打扰默认策略。",
                "SAFE_DEFAULT",
                false,
                now),
            currentActor());
        return new SystemSettings(
            readPayload(config.value(), "租户通知默认策略已损坏"),
            config.version(),
            config.updatedAt());
    }

    private static Map<String, Object> auditSnapshot(SettingsPayload payload) {
        return Map.of(
            "inAppEnabled", payload.inAppEnabled(),
            "externalChannelCount", enabledExternalChannelCount(payload),
            "quietHoursEnabled", payload.quietHoursEnabled(),
            "quietWindow", payload.quietStart() + "-" + payload.quietEnd(),
            "subscribedTypes", payload.subscribedTypes().stream().map(Enum::name).toList());
    }

    private static long enabledExternalChannelCount(SettingsPayload payload) {
        return java.util.stream.Stream.of(
                payload.smsEnabled(),
                payload.emailEnabled(),
                payload.pushEnabled(),
                payload.webhookEnabled(),
                payload.inHospitalMessageEnabled())
            .filter(Boolean::booleanValue)
            .count();
    }

    private static String normalizeTime(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        LocalTime.parse(normalized);
        return normalized;
    }

    private static boolean quietActive(String start, String end, LocalTime now) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        if (startTime.equals(endTime)) {
            return true;
        }
        if (startTime.isBefore(endTime)) {
            return !now.isBefore(startTime) && now.isBefore(endTime);
        }
        return !now.isBefore(startTime) || now.isBefore(endTime);
    }

    private String requireTenantId() {
        OrgScope scope = RequestContext.currentOrgScope();
        String tenantId = scope.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String requireUserId() {
        return RequestContext.currentUserId()
            .filter(userId -> !userId.isBlank())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "当前用户上下文缺失"));
    }

    private String currentActor() {
        return RequestContext.currentUserId()
            .filter(userId -> !userId.isBlank())
            .orElse("system");
    }

    private static boolean defaultTrue(Boolean value) {
        return value == null || value;
    }

    private static boolean defaultFalse(Boolean value) {
        return value != null && value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private record SettingsPayload(
        boolean inAppEnabled,
        boolean smsEnabled,
        boolean emailEnabled,
        boolean pushEnabled,
        boolean webhookEnabled,
        boolean inHospitalMessageEnabled,
        boolean quietHoursEnabled,
        String quietStart,
        String quietEnd,
        Set<WorkflowNotificationLevel> quietBypassLevels,
        Set<WorkflowNotificationType> subscribedTypes
    ) {
    }

    private record SystemSettings(
        SettingsPayload payload,
        long version,
        Instant updatedAt
    ) {
    }
}
