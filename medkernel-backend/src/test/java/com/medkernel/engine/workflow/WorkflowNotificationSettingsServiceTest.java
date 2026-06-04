package com.medkernel.engine.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.experience.UserPreference;
import com.medkernel.engine.experience.UserPreferenceRepository;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowNotificationSettingsServiceTest {

    private UserPreferenceRepository repository;
    private WorkflowNotificationSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserPreferenceRepository.class);
        service = new WorkflowNotificationSettingsService(repository, new ObjectMapper());
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-notify-settings",
            OrgScope.tenant("tenant-A"),
            "doctor-1"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void getSettingsReturnsSafeDefaultsWithoutPretendingExternalChannelsAreConnected() {
        when(repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                "tenant-A",
                "doctor-1",
                "notification.settings",
                "ACTIVE"))
            .thenReturn(Optional.empty());

        WorkflowNotificationSettingsResponse response = service.getSettings();

        assertThat(response.inAppEnabled()).isTrue();
        assertThat(response.smsEnabled()).isFalse();
        assertThat(response.emailEnabled()).isFalse();
        assertThat(response.pushEnabled()).isFalse();
        assertThat(response.quietHoursEnabled()).isFalse();
        assertThat(response.quietBypassLevels())
            .containsExactly(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH);
    }

    @Test
    void saveSettingsPersistsCurrentTenantUserAndKeepsCriticalLevelsBypassingQuietHours() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        UserPreference existing = new UserPreference(
            "pref-notify-1",
            "tenant-A",
            "doctor-1",
            "notification.settings",
            "{}",
            2,
            "ACTIVE",
            now,
            "doctor-1",
            now,
            "doctor-1");
        when(repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                "tenant-A",
                "doctor-1",
                "notification.settings",
                "ACTIVE"))
            .thenReturn(Optional.of(existing));
        when(repository.save(any(UserPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowNotificationSettingsResponse response = service.saveSettings(new WorkflowNotificationSettingsRequest(
            true,
            true,
            false,
            false,
            true,
            "22:00",
            "07:00",
            Set.of(WorkflowNotificationLevel.INFO)));

        assertThat(response.version()).isEqualTo(3);
        assertThat(response.quietBypassLevels())
            .containsExactly(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH, WorkflowNotificationLevel.INFO);
        verify(repository).save(any(UserPreference.class));
    }

    @Test
    void quietHoursSuppressLowDisturbanceDeliveryButNeverCriticalOrHighLevels() {
        WorkflowNotificationSettingsResponse settings = new WorkflowNotificationSettingsResponse(
            true,
            true,
            false,
            false,
            true,
            "22:00",
            "07:00",
            Set.of(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH),
            true,
            1,
            Instant.parse("2026-06-04T08:00:00Z"),
            "doctor-1");

        assertThat(service.isMutedByQuietHours(WorkflowNotificationLevel.INFO, settings, LocalTime.parse("23:30")))
            .isTrue();
        assertThat(service.isMutedByQuietHours(WorkflowNotificationLevel.CRITICAL, settings, LocalTime.parse("23:30")))
            .isFalse();
        assertThat(service.isMutedByQuietHours(WorkflowNotificationLevel.HIGH, settings, LocalTime.parse("23:30")))
            .isFalse();
    }
}
