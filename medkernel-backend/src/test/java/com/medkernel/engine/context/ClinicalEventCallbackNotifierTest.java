package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.integration.domain.IntegrationWebhookConfig;
import com.medkernel.engine.integration.dto.IntegrationOutboundRequestDto;
import com.medkernel.engine.integration.dto.IntegrationOutboundResultDto;
import com.medkernel.engine.integration.repository.IntegrationWebhookConfigRepository;
import com.medkernel.engine.integration.service.IntegrationService;
import com.medkernel.shared.context.OrgScope;

/**
 * 临床事件客户回调出站登记契约。
 */
class ClinicalEventCallbackNotifierTest {

    private ClinicalEventRepository events;
    private IntegrationWebhookConfigRepository webhooks;
    private IntegrationService integration;
    private ClinicalEventCallbackNotifier notifier;

    @BeforeEach
    void setUp() {
        events = mock(ClinicalEventRepository.class);
        webhooks = mock(IntegrationWebhookConfigRepository.class);
        integration = mock(IntegrationService.class);
        notifier = new ClinicalEventCallbackNotifier(events, webhooks, integration,
            new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void processedEventRegistersSanitizedOutboundCallback() {
        ClinicalEvent event = event("wh-clinical");
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(event));
        when(webhooks.findByWebhookIdAndTenantId("wh-clinical", "tenant-A"))
            .thenReturn(Optional.of(webhook("wh-clinical")));
        when(integration.enqueueOutboundMessage(eq("tenant-A"), any()))
            .thenReturn(new IntegrationOutboundResultDto(
                "clinical-callback-evt-1", "trace-1", "wh-clinical",
                "NOT_CONNECTED", false, true, "出站消息已登记"));

        notifier.notifyProcessed(new ClinicalEventProcessedEvent(
            "evt-1", "tenant-A", "trace-1", context()));

        ArgumentCaptor<IntegrationOutboundRequestDto> requestCap =
            ArgumentCaptor.forClass(IntegrationOutboundRequestDto.class);
        verify(integration).enqueueOutboundMessage(eq("tenant-A"), requestCap.capture());
        IntegrationOutboundRequestDto request = requestCap.getValue();
        assertThat(request.messageId()).isEqualTo("clinical-callback-evt-1");
        assertThat(request.adapterId()).isEqualTo("wh-clinical");
        assertThat(request.protocolType()).isEqualTo("Webhook");
        assertThat(request.payloadSummary()).contains("临床事件处理完成回调");
        assertThat(request.payload().path("eventId").asText()).isEqualTo("evt-1");
        assertThat(request.payload().path("status").asText()).isEqualTo("PROCESSED");
        assertThat(request.payload().path("payloadDigest").asText()).isEqualTo("digest");
        assertThat(request.payload().toString()).doesNotContain("secret");
        assertThat(request.payload().has("rawPayload")).isFalse();
    }

    @Test
    void processedEventWithoutCallbackDoesNotRegisterOutboundMessage() {
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(event(null)));

        notifier.notifyProcessed(new ClinicalEventProcessedEvent(
            "evt-1", "tenant-A", "trace-1", context()));

        verify(integration, never()).enqueueOutboundMessage(any(), any());
    }

    private ClinicalEvent event(String callbackWebhookId) {
        return new ClinicalEvent(
            1L, "evt-1", "tenant-A", ClinicalEventType.DIAGNOSIS,
            ClinicalEventTriggerPoint.PATIENT_VIEW, "idem-1", callbackWebhookId,
            "{\"tenantId\":\"tenant-A\",\"departmentId\":\"dept-A\"}",
            "MPI-1", "ENC-1", "HIS", "kpv-1", "digest",
            Instant.parse("2026-05-27T01:00:00Z"), Instant.parse("2026-05-27T01:00:01Z"),
            null, ClinicalEventStatus.PROCESSED, null, null, 0, null, "trace-1");
    }

    private IntegrationWebhookConfig webhook(String webhookId) {
        return new IntegrationWebhookConfig(
            1L, webhookId, "tenant-A", "HIS 事件回调",
            "https://his.example.test/callback", "secret-should-not-leak",
            "CLINICAL_EVENT", "ACTIVE", Instant.now(), "tester", Instant.now(), "tester");
    }

    private ClinicalEventContext context() {
        return new ClinicalEventContext(
            "evt-1", "tenant-A", new OrgScope(
                "tenant-A", "group-A", "hospital-A", "campus-A", "site-A", "dept-A", "specialty-A"),
            ClinicalEventType.DIAGNOSIS, ClinicalEventTriggerPoint.PATIENT_VIEW, "MPI-1", "ENC-1", null, "HIS",
            "kpv-1", "digest", Instant.parse("2026-05-27T01:00:00Z"),
            "HIS:patient-view", "trace-1", null, List.of());
    }
}
