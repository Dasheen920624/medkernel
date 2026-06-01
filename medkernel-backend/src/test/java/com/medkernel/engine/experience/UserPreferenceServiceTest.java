package com.medkernel.engine.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class UserPreferenceServiceTest {

    private UserPreferenceRepository repository;
    private UserPreferenceService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserPreferenceRepository.class);
        service = new UserPreferenceService(repository);
        RequestContext.restore(new RequestContext.Snapshot("trace-theme", OrgScope.tenant("tenant-1"), "doctor-1"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void saveThemePreference_upsertsCurrentTenantAndUserThemeMode() {
        UserPreference existing = preference("pref-01", "tenant-1", "doctor-1", "theme.mode", "default", 2);
        when(repository.findByTenantIdAndUserIdAndPrefKeyAndStatus("tenant-1", "doctor-1", "theme.mode", "ACTIVE"))
            .thenReturn(Optional.of(existing));
        when(repository.save(any(UserPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ThemePreferenceResponse response = service.saveThemePreference(new ThemePreferenceRequest("elder"));

        assertThat(response.mode()).isEqualTo("elder");
        assertThat(response.version()).isEqualTo(3);
        assertThat(response.updatedBy()).isEqualTo("doctor-1");
        verify(repository).save(any(UserPreference.class));
    }

    @Test
    void getThemePreference_readsOnlyCurrentTenantAndUser() {
        when(repository.findByTenantIdAndUserIdAndPrefKeyAndStatus("tenant-1", "doctor-1", "theme.mode", "ACTIVE"))
            .thenReturn(Optional.of(preference("pref-01", "tenant-1", "doctor-1", "theme.mode", "dark", 1)));

        ThemePreferenceResponse response = service.getThemePreference();

        assertThat(response.mode()).isEqualTo("dark");
    }

    @Test
    void getThemePreference_returnsDefaultWhenCurrentUserHasNoPreference() {
        when(repository.findByTenantIdAndUserIdAndPrefKeyAndStatus("tenant-1", "doctor-1", "theme.mode", "ACTIVE"))
            .thenReturn(Optional.empty());

        ThemePreferenceResponse response = service.getThemePreference();

        assertThat(response.mode()).isEqualTo("default");
        assertThat(response.version()).isZero();
    }

    @Test
    void saveThemePreference_rejectsUnsupportedMode() {
        assertThatThrownBy(() -> service.saveThemePreference(new ThemePreferenceRequest("contrast")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("主题模式");
    }

    private static UserPreference preference(
        String id,
        String tenantId,
        String userId,
        String prefKey,
        String prefValue,
        long version
    ) {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new UserPreference(
            id,
            tenantId,
            userId,
            prefKey,
            prefValue,
            version,
            "ACTIVE",
            now,
            userId,
            now,
            userId
        );
    }
}
