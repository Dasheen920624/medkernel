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
        assertThat(response.webhookEnabled()).isFalse();
        assertThat(response.inHospitalMessageEnabled()).isFalse();
        assertThat(response.quietHoursEnabled()).isFalse();
        assertThat(response.quietBypassLevels())
            .containsExactly(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH);
    }

    @Test
    void getSettingsForUserReadsRecipientPreferenceInsteadOfCurrentActor() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        UserPreference recipientPreference = new UserPreference(
            "pref-notify-nurse-2",
            "tenant-A",
            "nurse-2",
            "notification.settings",
            """
                {
                  "inAppEnabled": true,
                  "smsEnabled": true,
                  "emailEnabled": false,
                  "pushEnabled": true,
                  "webhookEnabled": true,
                  "inHospitalMessageEnabled": true,
                  "quietHoursEnabled": true,
                  "quietStart": "21:30",
                  "quietEnd": "06:30",
                  "quietBypassLevels": ["CRITICAL", "HIGH"]
                }
                """,
            5,
            "ACTIVE",
            now,
            "nurse-2",
            now,
            "nurse-2");
        when(repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                "tenant-A",
                "nurse-2",
                "notification.settings",
                "ACTIVE"))
            .thenReturn(Optional.of(recipientPreference));

        WorkflowNotificationSettingsResponse response = service.getSettingsForUser("tenant-A", "nurse-2");

        assertThat(response.smsEnabled()).isTrue();
        assertThat(response.emailEnabled()).isFalse();
        assertThat(response.pushEnabled()).isTrue();
        assertThat(response.webhookEnabled()).isTrue();
        assertThat(response.inHospitalMessageEnabled()).isTrue();
        assertThat(response.quietHoursEnabled()).isTrue();
        assertThat(response.quietStart()).isEqualTo("21:30");
        assertThat(response.version()).isEqualTo(5);
        verify(repository).findByTenantIdAndUserIdAndPrefKeyAndStatus(
            "tenant-A",
            "nurse-2",
            "notification.settings",
            "ACTIVE");
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
            true,
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
            true,
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
