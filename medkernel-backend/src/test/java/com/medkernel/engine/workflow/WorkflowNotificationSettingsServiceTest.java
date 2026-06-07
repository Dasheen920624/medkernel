package com.medkernel.engine.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.experience.UserPreference;
import com.medkernel.engine.experience.UserPreferenceRepository;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.config.SystemConfigItemResponse;
import com.medkernel.shared.config.SystemConfigSeed;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.config.SystemConfigUpdateRequest;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowNotificationSettingsServiceTest {

    private UserPreferenceRepository repository;
    private SystemConfigService systemConfigService;
    private AuditRecorder auditRecorder;
    private WorkflowNotificationSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserPreferenceRepository.class);
        systemConfigService = mock(SystemConfigService.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new WorkflowNotificationSettingsService(
            repository,
            new ObjectMapper(),
            systemConfigService,
            auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-notify-settings",
            OrgScope.tenant("tenant-A"),
            "doctor-1"));
        when(systemConfigService.getOrSeedTenantConfig(
                eq("tenant-A"),
                eq(WorkflowNotificationSettingsService.SYSTEM_DEFAULTS_KEY),
                any(),
                any()))
            .thenReturn(systemDefaults());
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void getSettingsInheritsTenantDefaultsWithoutPretendingExternalChannelsAreConnected() {
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
        assertThat(response.subscribedTypes())
            .containsExactly(
                WorkflowNotificationType.SAFETY,
                WorkflowNotificationType.FOLLOWUP,
                WorkflowNotificationType.WORKFLOW,
                WorkflowNotificationType.SYNC);
        assertThat(response.mandatoryTypes()).containsExactly(WorkflowNotificationType.SAFETY);
        assertThat(response.source()).isEqualTo(WorkflowNotificationSettingsSource.SYSTEM_DEFAULT);
        assertThat(response.systemVersion()).isEqualTo(7);

        ArgumentCaptor<SystemConfigSeed> seedCaptor = ArgumentCaptor.forClass(SystemConfigSeed.class);
        verify(systemConfigService).getOrSeedTenantConfig(
            eq("tenant-A"),
            eq(WorkflowNotificationSettingsService.SYSTEM_DEFAULTS_KEY),
            seedCaptor.capture(),
            eq("doctor-1"));
        assertThat(seedCaptor.getValue().source()).isEqualTo("API");
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
                  "quietBypassLevels": ["CRITICAL", "HIGH"],
                  "subscribedTypes": ["SAFETY", "FOLLOWUP"]
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
        assertThat(response.subscribedTypes())
            .containsExactly(WorkflowNotificationType.SAFETY, WorkflowNotificationType.FOLLOWUP);
        assertThat(response.source()).isEqualTo(WorkflowNotificationSettingsSource.PERSONAL);
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
            Set.of(WorkflowNotificationLevel.INFO),
            Set.of(WorkflowNotificationType.FOLLOWUP)));

        assertThat(response.version()).isEqualTo(3);
        assertThat(response.quietBypassLevels())
            .containsExactly(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH, WorkflowNotificationLevel.INFO);
        assertThat(response.subscribedTypes())
            .containsExactly(WorkflowNotificationType.SAFETY, WorkflowNotificationType.FOLLOWUP);
        verify(repository).save(any(UserPreference.class));
        verify(auditRecorder).record(any());
    }

    @Test
    void saveSystemSettingsUsesTenantConfigurationCenterWithOptimisticVersionAndReason() {
        when(systemConfigService.updateTenant(
                eq("tenant-A"),
                eq(WorkflowNotificationSettingsService.SYSTEM_DEFAULTS_KEY),
                any(SystemConfigUpdateRequest.class),
                eq("doctor-1")))
            .thenReturn(new SystemConfigItemResponse(
                WorkflowNotificationSettingsService.SYSTEM_DEFAULTS_KEY,
                """
                    {
                      "inAppEnabled": true,
                      "smsEnabled": true,
                      "emailEnabled": false,
                      "pushEnabled": false,
                      "webhookEnabled": false,
                      "inHospitalMessageEnabled": true,
                      "quietHoursEnabled": true,
                      "quietStart": "21:00",
                      "quietEnd": "07:30",
                      "quietBypassLevels": ["CRITICAL", "HIGH"],
                      "subscribedTypes": ["SAFETY", "WORKFLOW"]
                    }
                    """,
                "JSON",
                "租户通知默认策略",
                "MEDIUM",
                "医院管理员",
                "租户通知渠道、订阅类型和免打扰默认策略。",
                "API",
                false,
                8,
                Instant.parse("2026-06-04T09:00:00Z")));

        WorkflowNotificationSettingsResponse response = service.saveSystemSettings(
            new WorkflowNotificationSystemSettingsRequest(
                new WorkflowNotificationSettingsRequest(
                    true,
                    true,
                    false,
                    false,
                    false,
                    true,
                    true,
                    "21:00",
                    "07:30",
                    Set.of(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH),
                Set.of(WorkflowNotificationType.WORKFLOW)),
                "统一夜间通知策略",
                7L));

        assertThat(response.source()).isEqualTo(WorkflowNotificationSettingsSource.SYSTEM_DEFAULT);
        assertThat(response.systemVersion()).isEqualTo(8);
        assertThat(response.subscribedTypes())
            .containsExactly(WorkflowNotificationType.SAFETY, WorkflowNotificationType.WORKFLOW);
        verify(systemConfigService).updateTenant(
            eq("tenant-A"),
            eq(WorkflowNotificationSettingsService.SYSTEM_DEFAULTS_KEY),
            any(SystemConfigUpdateRequest.class),
            eq("doctor-1"));
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
            Set.of(WorkflowNotificationType.SAFETY, WorkflowNotificationType.WORKFLOW),
            Set.of(WorkflowNotificationType.SAFETY),
            WorkflowNotificationSettingsSource.PERSONAL,
            true,
            1,
            7,
            Instant.parse("2026-06-04T08:00:00Z"),
            "doctor-1");

        assertThat(service.isMutedByQuietHours(WorkflowNotificationLevel.INFO, settings, LocalTime.parse("23:30")))
            .isTrue();
        assertThat(service.isMutedByQuietHours(WorkflowNotificationLevel.CRITICAL, settings, LocalTime.parse("23:30")))
            .isFalse();
        assertThat(service.isMutedByQuietHours(WorkflowNotificationLevel.HIGH, settings, LocalTime.parse("23:30")))
            .isFalse();
    }

    @Test
    void unsubscribedOrdinaryNotificationIsMutedButSafetyNotificationAlwaysRemainsSubscribed() {
        WorkflowNotificationSettingsResponse settings = settingsWithSubscriptions(
            Set.of(WorkflowNotificationType.SAFETY, WorkflowNotificationType.FOLLOWUP));

        assertThat(service.isSubscribed(WorkflowNotificationSourceType.WORKFLOW_TODO,
            WorkflowNotificationLevel.INFO, settings)).isFalse();
        assertThat(service.isSubscribed(WorkflowNotificationSourceType.SAFETY_REVIEW,
            WorkflowNotificationLevel.INFO, settings)).isTrue();
        assertThat(service.isSubscribed(WorkflowNotificationSourceType.WORKFLOW_TODO,
            WorkflowNotificationLevel.CRITICAL, settings)).isTrue();
    }

    private static WorkflowNotificationSettingsResponse settingsWithSubscriptions(
            Set<WorkflowNotificationType> subscriptions) {
        return new WorkflowNotificationSettingsResponse(
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            "22:00",
            "07:00",
            Set.of(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH),
            subscriptions,
            Set.of(WorkflowNotificationType.SAFETY),
            WorkflowNotificationSettingsSource.PERSONAL,
            false,
            1,
            7,
            Instant.parse("2026-06-04T08:00:00Z"),
            "doctor-1");
    }

    private static SystemConfigItemResponse systemDefaults() {
        return new SystemConfigItemResponse(
            WorkflowNotificationSettingsService.SYSTEM_DEFAULTS_KEY,
            """
                {
                  "inAppEnabled": true,
                  "smsEnabled": false,
                  "emailEnabled": false,
                  "pushEnabled": false,
                  "webhookEnabled": false,
                  "inHospitalMessageEnabled": false,
                  "quietHoursEnabled": false,
                  "quietStart": "22:00",
                  "quietEnd": "07:00",
                  "quietBypassLevels": ["CRITICAL", "HIGH"],
                  "subscribedTypes": ["SAFETY", "FOLLOWUP", "WORKFLOW", "SYNC"]
                }
                """,
            "JSON",
            "租户通知默认策略",
            "MEDIUM",
            "医院管理员",
            "租户通知渠道、订阅类型和免打扰默认策略。",
            "SEED",
            false,
            7,
            Instant.parse("2026-06-04T08:00:00Z"));
    }
}
