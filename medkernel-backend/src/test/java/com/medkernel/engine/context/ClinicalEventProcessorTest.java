package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.observability.StateTransitionRecorder;
import com.medkernel.shared.observability.TransitionError;

class ClinicalEventProcessorTest {

    private ClinicalEventRepository events;
    private ClinicalEventPayloadRepository payloads;
    private AuditRecorder auditRecorder;
    private AuditEventPublisher auditPublisher;
    private StateTransitionRecorder transitions;
    private ApplicationEventPublisher applicationEvents;
    private CapturingAdapter ruleAdapter;
    private CapturingAdapter pathwayAdapter;
    private CapturingAdapter cdssAdapter;
    private ClinicalEventProcessor processor;

    @BeforeEach
    void setUp() {
        events = mock(ClinicalEventRepository.class);
        payloads = mock(ClinicalEventPayloadRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        auditPublisher = mock(AuditEventPublisher.class);
        transitions = mock(StateTransitionRecorder.class);
        applicationEvents = mock(ApplicationEventPublisher.class);
        ruleAdapter = new CapturingAdapter(ClinicalEventEngine.RULE);
        pathwayAdapter = new CapturingAdapter(ClinicalEventEngine.PATHWAY);
        cdssAdapter = new CapturingAdapter(ClinicalEventEngine.CDSS);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        processor = new ClinicalEventProcessor(
            events, payloads, auditRecorder, auditPublisher, transitions, applicationEvents,
            new ClinicalEventContextFactory(json),
            new ClinicalEventEngineDispatcher(List.of(ruleAdapter, pathwayAdapter, cdssAdapter)));
        when(events.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void processMovesReceivedEventToMappedThenProcessed() {
        ClinicalEvent event = event(ClinicalEventStatus.RECEIVED);
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(event));
        when(payloads.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(payload()));

        processor.process("evt-1", "tenant-A");

        ArgumentCaptor<ClinicalEvent> eventCap = ArgumentCaptor.forClass(ClinicalEvent.class);
        verify(events, org.mockito.Mockito.times(2)).save(eventCap.capture());
        org.assertj.core.api.Assertions.assertThat(eventCap.getAllValues())
            .extracting(ClinicalEvent::processingStatus)
            .containsExactly(ClinicalEventStatus.MAPPED, ClinicalEventStatus.PROCESSED);
        verify(transitions).record("clinical_event", "evt-1",
            "RECEIVED", "MAPPED", "TERMINOLOGY_OK", null);
        verify(transitions).record("clinical_event", "evt-1",
            "MAPPED", "PROCESSED", "ENGINES_OK", null);
        verify(auditRecorder).record(AuditAction.EXECUTE, "clinical_event", "evt-1",
            "处理临床事件成功 type=DIAGNOSIS");
        verify(applicationEvents).publishEvent(any(ClinicalEventProcessedEvent.class));

        assertThat(ruleAdapter.contexts()).hasSize(1);
        assertThat(pathwayAdapter.contexts()).containsExactly(ruleAdapter.contexts().get(0));
        assertThat(cdssAdapter.contexts()).containsExactly(ruleAdapter.contexts().get(0));
        ClinicalEventContext context = ruleAdapter.contexts().get(0);
        assertThat(context.eventId()).isEqualTo("evt-1");
        assertThat(context.tenantId()).isEqualTo("tenant-A");
        assertThat(context.patientId()).isEqualTo("MPI-1");
        assertThat(context.encounterId()).isEqualTo("ENC-1");
        assertThat(context.orgScope().departmentId()).isEqualTo("dept-A");
        assertThat(context.payload().path("a").asInt()).isEqualTo(1);
        assertThat(context.payloadDigest()).isEqualTo("digest");
    }

    @Test
    void processFailsWhenPayloadMissing() {
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(event(ClinicalEventStatus.RECEIVED)));
        when(payloads.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process("evt-1", "tenant-A"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_OBS_001);
    }

    @Test
    void markFailedStoresErrorStatusTransitionAndAudit() {
        Instant nextRetryAt = Instant.parse("2026-05-27T01:01:00Z");
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A"))
            .thenReturn(Optional.of(event(ClinicalEventStatus.MAPPED)));

        processor.markFailed("evt-1", "tenant-A", ErrorCode.ENG_EVENT_004, 2, false, nextRetryAt);

        ArgumentCaptor<ClinicalEvent> eventCap = ArgumentCaptor.forClass(ClinicalEvent.class);
        verify(events).save(eventCap.capture());
        assertThat(eventCap.getValue().processingStatus()).isEqualTo(ClinicalEventStatus.FAILED);
        assertThat(eventCap.getValue().errorCode()).isEqualTo(ErrorCode.ENG_EVENT_004.code());
        assertThat(eventCap.getValue().errorClass()).isEqualTo(ErrorCode.ENG_EVENT_004.errorClass().name());
        assertThat(eventCap.getValue().retryCount()).isEqualTo(2);

        ArgumentCaptor<TransitionError> errorCap = ArgumentCaptor.forClass(TransitionError.class);
        verify(transitions).record(eq("clinical_event"), eq("evt-1"),
            eq("MAPPED"), eq("FAILED"), eq("PROCESS_FAILED"), errorCap.capture());
        assertThat(errorCap.getValue().errorCode()).isEqualTo(ErrorCode.ENG_EVENT_004.code());
        assertThat(errorCap.getValue().retryCount()).isEqualTo(2);
        assertThat(errorCap.getValue().nextRetryAt()).isEqualTo(nextRetryAt);

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCap.capture());
        assertThat(auditCap.getValue().outcome()).isEqualTo(AuditEvent.OUTCOME_FAILED);
        assertThat(auditCap.getValue().errorCode()).isEqualTo(ErrorCode.ENG_EVENT_004.code());
    }

    private ClinicalEvent event(ClinicalEventStatus status) {
        return new ClinicalEvent(
            1L, "evt-1", "tenant-A", ClinicalEventType.DIAGNOSIS,
            ClinicalEventTriggerPoint.PATIENT_VIEW, null, null,
            "{\"tenantId\":\"tenant-A\",\"departmentId\":\"dept-A\"}",
            "MPI-1", "ENC-1", "HIS", "kpv-1", "digest",
            Instant.parse("2026-05-27T01:00:00Z"), Instant.parse("2026-05-27T01:00:01Z"),
            null, status, null, null, 0, null, "trace-1");
    }

    private ClinicalEventPayload payload() {
        return new ClinicalEventPayload(
            1L, "evt-1", "tenant-A", "{\"a\":1}", null,
            "INLINE", "application/json", "digest", 7L, Instant.now(), null);
    }

    private static final class CapturingAdapter implements ClinicalEventEngineAdapter {
        private final ClinicalEventEngine engine;
        private final List<ClinicalEventContext> contexts = new ArrayList<>();

        private CapturingAdapter(ClinicalEventEngine engine) {
            this.engine = engine;
        }

        @Override
        public ClinicalEventEngine engine() {
            return engine;
        }

        @Override
        public ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context) {
            contexts.add(context);
            return ClinicalEventEngineDispatchResult.dispatched(engine, "ok-" + engine.name(), "已接收");
        }

        private List<ClinicalEventContext> contexts() {
            return contexts;
        }
    }
}
