package com.medkernel.engine.experience;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 用户体验偏好服务。
 */
@Service
public class UserPreferenceService {

    private static final String ACTIVE = "ACTIVE";
    private static final String THEME_MODE_KEY = "theme.mode";
    private static final Set<String> THEME_MODES = Set.of("default", "elder", "dark", "eye", "system");

    private final UserPreferenceRepository repository;

    public UserPreferenceService(UserPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ThemePreferenceResponse getThemePreference() {
        String tenantId = requireTenantId();
        String userId = requireUserId();
        return repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(tenantId, userId, THEME_MODE_KEY, ACTIVE)
            .map(ThemePreferenceResponse::from)
            .orElseGet(ThemePreferenceResponse::defaultMode);
    }

    @Transactional
    public ThemePreferenceResponse saveThemePreference(ThemePreferenceRequest request) {
        String mode = normalizeThemeMode(request == null ? null : request.mode());
        String tenantId = requireTenantId();
        String userId = requireUserId();
        Instant now = Instant.now();

        UserPreference preference = repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                tenantId, userId, THEME_MODE_KEY, ACTIVE)
            .map(existing -> existing.updateValue(mode, userId, now))
            .orElseGet(() -> UserPreference.create(newId(), tenantId, userId, THEME_MODE_KEY, mode, now));

        return ThemePreferenceResponse.from(repository.save(preference));
    }

    private String normalizeThemeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (!THEME_MODES.contains(normalized)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "不支持的主题模式，请选择 default、elder、dark、eye 或 system");
        }
        return normalized;
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

    private String newId() {
        return "up-" + UUID.randomUUID();
    }
}
