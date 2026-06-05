package com.medkernel.engine.workflow;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.experience.UserPreference;
import com.medkernel.engine.experience.UserPreferenceRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知偏好与免打扰策略服务。
 *
 * <p>当前保存用户个人偏好；外部通道启用后仅用于登记出站补偿，不声明短信、移动推送等通道已真实投递。
 */
@Service
public class WorkflowNotificationSettingsService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PREF_KEY = "notification.settings";
    private static final String DEFAULT_QUIET_START = "22:00";
    private static final String DEFAULT_QUIET_END = "07:00";
    private static final Set<WorkflowNotificationLevel> SAFETY_BYPASS_LEVELS =
        Set.of(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH);

    private final UserPreferenceRepository repository;
    private final ObjectMapper objectMapper;

    public WorkflowNotificationSettingsService(UserPreferenceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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
        return repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                normalizedTenantId, normalizedUserId, PREF_KEY, ACTIVE)
            .map(this::responseFromPreference)
            .orElseGet(() -> response(defaultPayload(), 0, null, null));
    }

    /**
     * 保存当前用户通知偏好。
     */
    @Transactional
    public WorkflowNotificationSettingsResponse saveSettings(WorkflowNotificationSettingsRequest request) {
        SettingsPayload payload = normalize(request);
        String tenantId = requireTenantId();
        String userId = requireUserId();
        Instant now = Instant.now();
        String value = writePayload(payload);

        UserPreference preference = repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                tenantId, userId, PREF_KEY, ACTIVE)
            .map(existing -> new UserPreference(
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
                userId))
            .orElseGet(() -> new UserPreference(
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
                userId));

        UserPreference saved = repository.save(preference);
        return response(payload, saved.version(), saved.updatedAt(), saved.updatedBy());
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

    private WorkflowNotificationSettingsResponse responseFromPreference(UserPreference preference) {
        return response(readPayload(preference), preference.version(), preference.updatedAt(), preference.updatedBy());
    }

    private WorkflowNotificationSettingsResponse response(
            SettingsPayload payload,
            long version,
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
            payload.quietHoursEnabled() && quietActive(payload.quietStart(), payload.quietEnd(), LocalTime.now()),
            version,
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
            normalizeBypassLevels(request.quietBypassLevels()));
    }

    private SettingsPayload readPayload(UserPreference preference) {
        try {
            return normalize(objectMapper.readValue(preference.prefValue(), SettingsPayload.class));
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.CONFLICT, "通知设置偏好已损坏，请重新保存");
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
            normalizeBypassLevels(payload.quietBypassLevels()));
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
            normalizeBypassLevels(null));
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
        Set<WorkflowNotificationLevel> quietBypassLevels
    ) {
    }
}
