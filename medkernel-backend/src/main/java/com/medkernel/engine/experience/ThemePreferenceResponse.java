package com.medkernel.engine.experience;

import java.time.Instant;

/**
 * 主题偏好响应。
 */
public record ThemePreferenceResponse(
    String mode,
    long version,
    Instant updatedAt,
    String updatedBy
) {

    static ThemePreferenceResponse from(UserPreference preference) {
        return new ThemePreferenceResponse(
            preference.prefValue(),
            preference.version(),
            preference.updatedAt(),
            preference.updatedBy()
        );
    }

    static ThemePreferenceResponse defaultMode() {
        return new ThemePreferenceResponse("default", 0, null, null);
    }
}
