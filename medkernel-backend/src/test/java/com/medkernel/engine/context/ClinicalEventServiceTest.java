package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;

class ClinicalEventServiceTest {

    private ClinicalEventRepository events;
    private ClinicalEventPayloadRepository payloads;
    private ClinicalEventOutboxRepository outbox;
    private ClinicalEventProcessor processor;
    private AuditEventPublisher auditPublisher;
    private StateTransitionRecorder transitions;
    private DiagnoseResponseAssembler diagnoseAssembler;
    private ObjectMapper json;
    private ClinicalEventService service;

    @BeforeEach
    void setUp() {
        events = mock(ClinicalEventRepository.class);
        payloads = mock(ClinicalEventPayloadRepository.class);
        outbox = mock(ClinicalEventOutboxRepository.class);
        processor = mock(ClinicalEventProcessor.class);
        auditPublisher = mock(AuditEventPublisher.class);
        transitions = mock(StateTransitionRecorder.class);
        diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        json = new ObjectMapper();
        json.findAndRegisterModules();

        service = new ClinicalEventService(
            events, payloads, outbox, processor, auditPublisher, transitions, diagnoseAssembler, json,
            new ClinicalEventProperties(1024, Duration.ofMillis(50), 10, 3, List.of(1L, 5L, 30L)));

        when(events.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(payloads.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outbox.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-event",
            new OrgScope("tenant-A", "group-A", "hospital-A", "campus-A", "site-A", "dept-A", "specialty-A"),
            "tester"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void receiveProcessesBeforeReturningAndClosesOutbox() {
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.empty());
        when(outbox.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(
            new ClinicalEventOutbox(42L, "evt-1", "tenant-A", "trace-event", "tester",
                "PENDING", null, null, Instant.now(), 0, null, Instant.now(), null)));

        ClinicalEventAcceptedResponse resp = service.receive(sampleRequest("evt-1"));

        assertThat(resp.eventId()).isEqualTo("evt-1");
        assertThat(resp.status()).isEqualTo(ClinicalEventStatus.PROCESSED);
        verify(processor).process("evt-1", "tenant-A");
        verify(outbox).markProcessed(eq(42L), any());
    }

    @Test
    void receiveAsyncPersistsEventPayloadOutboxAndHistory() {
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.empty());

        ClinicalEventAcceptedResponse resp = service.receiveAsync(sampleRequest("evt-1"));

        assertThat(resp.eventId()).isEqualTo("evt-1");
        assertThat(resp.status()).isEqualTo(ClinicalEventStatus.RECEIVED);
        assertThat(resp.traceId()).isEqualTo("trace-event");

        ArgumentCaptor<ClinicalEvent> eventCap = ArgumentCaptor.forClass(ClinicalEvent.class);
        verify(events).save(eventCap.capture());
        assertThat(eventCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(eventCap.getValue().patientId()).isEqualTo("MPI-1");
        assertThat(eventCap.getValue().encounterId()).isEqualTo("ENC-1");
        assertThat(eventCap.getValue().orgScopeJson()).contains("\"tenantId\":\"tenant-A\"");
        assertThat(eventCap.getValue().orgScopeJson()).contains("\"departmentId\":\"dept-A\"");
        assertThat(eventCap.getValue().processingStatus()).isEqualTo(ClinicalEventStatus.RECEIVED);
        assertThat(eventCap.getValue().traceId()).isEqualTo("trace-event");

        ArgumentCaptor<ClinicalEventPayload> payloadCap = ArgumentCaptor.forClass(ClinicalEventPayload.class);
        verify(payloads).save(payloadCap.capture());
        assertThat(payloadCap.getValue().eventId()).isEqualTo("evt-1");
        assertThat(payloadCap.getValue().digest()).isEqualTo(resp.payloadDigest());
        assertThat(payloadCap.getValue().storageType()).isEqualTo("INLINE");

        ArgumentCaptor<ClinicalEventOutbox> outboxCap = ArgumentCaptor.forClass(ClinicalEventOutbox.class);
        verify(outbox).save(outboxCap.capture());
        assertThat(outboxCap.getValue().claimStatus()).isEqualTo("PENDING");

        verify(transitions).record("clinical_event", "evt-1", null, "RECEIVED", "INITIAL_RECEIVE", null);
        verify(auditPublisher).publish(AuditAction.CREATE, "clinical_event", "evt-1",
            "接收临床事件 type=DIAGNOSIS patient=MPI-1");
    }

    @Test
    void receiveAsyncIsIdempotentWhenSameEventIdAndPayloadDigest() {
        ClinicalEvent existing = existingEvent("evt-1", ClinicalEventStatus.RECEIVED, digest(samplePayload()));
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(existing));

        ClinicalEventAcceptedResponse resp = service.receiveAsync(sampleRequest("evt-1"));

        assertThat(resp.eventId()).isEqualTo("evt-1");
        assertThat(resp.payloadDigest()).isEqualTo(existing.payloadDigest());
        verify(events, never()).save(any());
        verify(payloads, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void receiveAsyncRejectsSameEventIdWithDifferentPayloadDigest() {
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A"))
            .thenReturn(Optional.of(existingEvent("evt-1", ClinicalEventStatus.RECEIVED, "different")));

        assertThatThrownBy(() -> service.receiveAsync(sampleRequest("evt-1")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVENT_002);
    }

    @Test
    void receiveAsyncRejectsOversizedPayload() {
        service = new ClinicalEventService(
            events, payloads, outbox, processor, auditPublisher, transitions, diagnoseAssembler, json,
            new ClinicalEventProperties(10, Duration.ofMillis(50), 10, 3, List.of(1L)));

        assertThatThrownBy(() -> service.receiveAsync(sampleRequest("evt-big")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVENT_001);
    }

    @Test
    void diagnoseIncludesPayloadSummary() {
        ClinicalEvent event = existingEvent("evt-1", ClinicalEventStatus.PROCESSED, "digest-1");
        ClinicalEventPayload payload = new ClinicalEventPayload(
            1L, "evt-1", "tenant-A", "{\"a\":1}", null,
            "INLINE", "application/json", "digest-1", 7L, Instant.now(), null);
        DiagnoseResponse expected = new DiagnoseResponse(
            "clinical_event", "evt-1", "tenant-A", "PROCESSED",
            event, List.of(), List.of(), Map.of(), null, "trace-event", null);
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(event));
        when(payloads.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(payload));
        when(diagnoseAssembler.assemble(eq("clinical_event"), eq("evt-1"), eq("tenant-A"),
            eq("PROCESSED"), eq(event), eq(List.of()), eq(Map.of()), any(), eq("trace-event")))
            .thenReturn(expected);

        DiagnoseResponse actual = service.diagnose("evt-1");

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void replayCreatesNewEventWithRootEventId() {
        ClinicalEvent source = existingEvent("evt-source", ClinicalEventStatus.FAILED, "digest-source");
        when(events.findByEventIdAndTenantId("evt-source", "tenant-A")).thenReturn(Optional.of(source));
        when(payloads.findByEventIdAndTenantId("evt-source", "tenant-A")).thenReturn(Optional.of(
            new ClinicalEventPayload(1L, "evt-source", "tenant-A", samplePayload().toString(), null,
                "INLINE", "application/json", "digest-source", 10L, Instant.now(), null)));
        when(events.findByEventIdAndTenantId(anyString(), eq("tenant-A"))).thenReturn(Optional.empty());
        when(events.findByEventIdAndTenantId("evt-source", "tenant-A")).thenReturn(Optional.of(source));

        ClinicalEventReplayResponse resp = service.replay("evt-source");

        assertThat(resp.sourceEventId()).isEqualTo("evt-source");
        assertThat(resp.newEventId()).startsWith("evt-replay-");
        ArgumentCaptor<ClinicalEvent> eventCap = ArgumentCaptor.forClass(ClinicalEvent.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).save(eventCap.capture());
        assertThat(eventCap.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.eventId()).isEqualTo(resp.newEventId());
            assertThat(saved.rootEventId()).isEqualTo("evt-source");
            assertThat(saved.processingStatus()).isEqualTo(ClinicalEventStatus.RECEIVED);
        });
    }

    @Test
    void listUsesRepositoryFilterWithinTenant() {
        ClinicalEvent row = existingEvent("evt-1", ClinicalEventStatus.PROCESSED, "digest");
        when(events.countByFilter("tenant-A", "MPI-1", null, "PROCESSED", null)).thenReturn(1L);
        when(events.pageByFilter("tenant-A", "MPI-1", null, "PROCESSED", null, 0, 20))
            .thenReturn(List.of(row));

        var page = service.list(
            new ClinicalEventFilter("MPI-1", null, ClinicalEventStatus.PROCESSED, null),
            PageRequest.defaults());

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(ClinicalEventDetailResponse::eventId).containsExactly("evt-1");
    }

    private ClinicalEventRequest sampleRequest(String eventId) {
        return new ClinicalEventRequest(
            eventId, ClinicalEventType.DIAGNOSIS, "MPI-1", "ENC-1",
            "HIS", "kpv-1", samplePayload(), Instant.parse("2026-05-27T01:00:00Z"));
    }

    private JsonNode samplePayload() {
        return json.createObjectNode()
            .put("diagnosisCode", "I21.0")
            .put("diagnosisName", "急性心肌梗死");
    }

    private ClinicalEvent existingEvent(String eventId, ClinicalEventStatus status, String digest) {
        return new ClinicalEvent(
            1L, eventId, "tenant-A", ClinicalEventType.DIAGNOSIS,
            "{\"tenantId\":\"tenant-A\",\"departmentId\":\"dept-A\"}",
            "MPI-1", "ENC-1", "HIS", "kpv-1", digest,
            Instant.parse("2026-05-27T01:00:00Z"), Instant.parse("2026-05-27T01:00:01Z"),
            null, status, null, null, 0, null, "trace-event");
    }

    private String digest(JsonNode payload) {
        return ClinicalEventService.digest(json, payload);
    }
}
