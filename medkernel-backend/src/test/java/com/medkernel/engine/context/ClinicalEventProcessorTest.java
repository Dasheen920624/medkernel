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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.canonical.ClinicalSetting;
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
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.StateTransitionRecorder;
import com.medkernel.shared.observability.TransitionError;

class ClinicalEventProcessorTest {

    private ClinicalEventRepository events;
    private ClinicalEventPayloadRepository payloads;
    private AuditRecorder auditRecorder;
    private AuditEventPublisher auditPublisher;
    private StateTransitionRecorder transitions;
    private ApplicationEventPublisher applicationEvents;
    private ContextSnapshotService contextSnapshots;
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
        contextSnapshots = mock(ContextSnapshotService.class);
        ruleAdapter = new CapturingAdapter(ClinicalEventEngine.RULE);
        pathwayAdapter = new CapturingAdapter(ClinicalEventEngine.PATHWAY);
        cdssAdapter = new CapturingAdapter(ClinicalEventEngine.CDSS);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        processor = new ClinicalEventProcessor(
            events, payloads, auditRecorder, auditPublisher, transitions, applicationEvents,
            new ClinicalEventContextFactory(json),
            new ClinicalEventEngineDispatcher(List.of(ruleAdapter, pathwayAdapter, cdssAdapter)),
            contextSnapshots);
        when(events.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contextSnapshots.createBound(
                any(ContextSnapshotRequest.class), any(), anyString()))
            .thenAnswer(inv -> {
                ContextSnapshotRequest req = inv.getArgument(0);
                return new ContextSnapshotResponse(
                    "ctx-event-" + req.requestId().replace("clinical-event:", ""),
                    ContextSnapshotStatus.ACTIVE,
                    req.resources(),
                    "runtime-release-test",
                    QualityStatus.VALID,
                    List.of(),
                    Map.of(),
                    Instant.parse("2026-05-27T01:00:02Z"),
                    req.traceId());
            });
    }

    @Test
    void processMovesReceivedEventToMappedThenProcessed() {
        ClinicalEvent event = event(ClinicalEventStatus.RECEIVED);
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(event));
        when(payloads.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(payload()));

        ClinicalEventStatus status = processor.process("evt-1", "tenant-A");

        assertThat(status).isEqualTo(ClinicalEventStatus.PROCESSED);
        ArgumentCaptor<ClinicalEvent> eventCap = ArgumentCaptor.forClass(ClinicalEvent.class);
        verify(events, org.mockito.Mockito.times(2)).save(eventCap.capture());
        org.assertj.core.api.Assertions.assertThat(eventCap.getAllValues())
            .extracting(ClinicalEvent::processingStatus)
            .containsExactly(ClinicalEventStatus.MAPPED, ClinicalEventStatus.PROCESSED);
        verify(transitions).record("clinical_event", "evt-1",
            "RECEIVED", "MAPPED", "TERMINOLOGY_OK", null);
        verify(transitions).record("clinical_event", "evt-1",
            "MAPPED", "PROCESSED", "ENGINES_OK", null);
        ArgumentCaptor<String> auditSummaryCap = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(AuditAction.EXECUTE), eq("clinical_event"), eq("evt-1"),
            auditSummaryCap.capture());
        assertThat(auditSummaryCap.getValue())
            .contains("处理临床事件成功 type=DIAGNOSIS")
            .contains("capabilities=")
            .doesNotContain("engines=")
            .contains("RULE:DISPATCHED:已接收")
            .contains("PATHWAY:DISPATCHED:已接收")
            .contains("CDSS:DISPATCHED:已接收");
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
        assertThat(context.contextSnapshotId()).isEqualTo("ctx-event-evt-1");
        assertThat(context.runtimeReleaseId()).isEqualTo("runtime-release-test");
        assertThat(context.payload().path("eventPayload").path("a").asInt()).isEqualTo(1);
        assertThat(context.payloadDigest()).isEqualTo("digest");
        ArgumentCaptor<ContextSnapshotRequest> snapshotCap = ArgumentCaptor.forClass(ContextSnapshotRequest.class);
        ArgumentCaptor<String> releaseIdCap = ArgumentCaptor.forClass(String.class);
        verify(contextSnapshots).createBound(
            snapshotCap.capture(), eq("clinical-event:evt-1"), releaseIdCap.capture());
        assertThat(snapshotCap.getValue().orgUnitId()).isEqualTo("dept-A");
        assertThat(releaseIdCap.getValue()).isEqualTo("runtime-release-test");
    }

    @Test
    void processCreatesSnapshotUnderPersistedEventOrgScopeWhenWorkerContextOnlyHasTenant() {
        ClinicalEvent event = event(ClinicalEventStatus.RECEIVED);
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(event));
        when(payloads.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(payload()));
        when(contextSnapshots.createBound(any(ContextSnapshotRequest.class), any(), anyString()))
            .thenAnswer(inv -> {
                OrgScope currentScope = RequestContext.currentOrgScope();
                assertThat(currentScope.hospitalId()).isEqualTo("hospital-A");
                assertThat(currentScope.departmentId()).isEqualTo("dept-A");
                ContextSnapshotRequest req = inv.getArgument(0);
                return new ContextSnapshotResponse(
                    "ctx-event-evt-1",
                    ContextSnapshotStatus.ACTIVE,
                    req.resources(),
                    "runtime-release-test",
                    QualityStatus.VALID,
                    List.of(),
                    Map.of(),
                    Instant.parse("2026-05-27T01:00:02Z"),
                    req.traceId());
            });
        RequestContext.restore(new RequestContext.Snapshot(
            "worker-trace", OrgScope.tenant("tenant-A"), "platform-admin"));

        try {
            ClinicalEventStatus status = processor.process("evt-1", "tenant-A");

            assertThat(status).isEqualTo(ClinicalEventStatus.PROCESSED);
        } finally {
            RequestContext.clear();
        }
    }

    @Test
    void markFailedRecordsTransitionUnderPersistedEventOrgScopeWhenWorkerContextOnlyHasTenant() {
        ClinicalEvent event = event(ClinicalEventStatus.RECEIVED);
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(event));
        org.mockito.Mockito.doAnswer(inv -> {
            OrgScope currentScope = RequestContext.currentOrgScope();
            assertThat(currentScope.hospitalId()).isEqualTo("hospital-A");
            assertThat(currentScope.departmentId()).isEqualTo("dept-A");
            return null;
        }).when(transitions).record(
            eq("clinical_event"), eq("evt-1"),
            eq("RECEIVED"), eq("FAILED"),
            eq("PROCESS_FAILED"), any(TransitionError.class));
        org.mockito.Mockito.doAnswer(inv -> {
            OrgScope currentScope = RequestContext.currentOrgScope();
            assertThat(currentScope.hospitalId()).isEqualTo("hospital-A");
            assertThat(currentScope.departmentId()).isEqualTo("dept-A");
            return null;
        }).when(auditPublisher).publish(any(AuditEvent.class));
        RequestContext.restore(new RequestContext.Snapshot(
            "worker-trace", OrgScope.tenant("tenant-A"), "platform-admin"));

        try {
            processor.markFailed(
                "evt-1", "tenant-A", ErrorCode.ENG_EVENT_005, 1, false,
                Instant.parse("2026-05-27T01:00:03Z"));
        } finally {
            RequestContext.clear();
        }
    }

    @Test
    void processProjectsOrderPayloadBeforeDispatchingRulePathwayAndCdss() {
        ClinicalEvent event = event(
            "evt-order",
            ClinicalEventType.ORDER,
            ClinicalEventTriggerPoint.ORDER_SIGN,
            ClinicalEventStatus.RECEIVED);
        when(events.findByEventIdAndTenantId("evt-order", "tenant-A")).thenReturn(Optional.of(event));
        when(payloads.findByEventIdAndTenantId("evt-order", "tenant-A"))
            .thenReturn(Optional.of(payload("evt-order", """
                {
                  "orders": [
                    {
                      "orderId": "ord-1",
                      "localCode": "HIS-AMOX",
                      "standardCode": "ATC-J01CA04",
                      "displayName": "阿莫西林",
                      "dose": 0.5,
                      "doseUnit": "g",
                      "route": "PO",
                      "frequency": "TID",
                      "status": "ACTIVE",
                      "sourceRecordId": "his-order-1",
                      "mappedVersion": "TERM-2026.06"
                    }
                  ]
                }
                """)));

        ClinicalEventStatus status = processor.process("evt-order", "tenant-A");

        assertThat(status).isEqualTo(ClinicalEventStatus.PROCESSED);
        assertThat(ruleAdapter.contexts()).hasSize(1);
        assertThat(pathwayAdapter.contexts()).containsExactly(ruleAdapter.contexts().get(0));
        assertThat(cdssAdapter.contexts()).containsExactly(ruleAdapter.contexts().get(0));
        ClinicalEventContext context = ruleAdapter.contexts().get(0);
        assertThat(context.triggerPoint()).isEqualTo("order-sign");
        assertThat(context.contextSnapshotId()).isEqualTo("ctx-event-evt-order");
        ArgumentCaptor<ContextSnapshotRequest> snapshotCap = ArgumentCaptor.forClass(ContextSnapshotRequest.class);
        verify(contextSnapshots).createBound(
            snapshotCap.capture(), eq("clinical-event:evt-order"), anyString());
        assertThat(snapshotCap.getValue().resources().medications())
            .singleElement()
            .satisfies(medication ->
                assertThat(medication.code()).isEqualTo("ATC-J01CA04"));
        assertThat(context.payload().path("medications").path(0).path("code").asText())
            .isEqualTo("ATC-J01CA04");
        assertThat(context.payload().path("medications").path(0).path("sourceRecordId").asText())
            .isEqualTo("his-order-1");
        assertThat(context.codeMappingAnchors()).anySatisfy(anchor -> {
            assertThat(anchor.resourceType()).isEqualTo(CanonicalResourceType.MEDICATION);
            assertThat(anchor.fieldName()).isEqualTo("code");
            assertThat(anchor.localCode()).isEqualTo("HIS-AMOX");
            assertThat(anchor.targetDictionaryKey()).isEqualTo("TERM.DRUG");
            assertThat(anchor.mappedVersion()).isEqualTo("TERM-2026.06");
        });
    }

    @Test
    void processProjectsPharmacyReviewInboundPayloadToCanonicalResourcesAndReviewExtension() {
        ClinicalEvent event = event(
            "evt-pharmacy-review",
            ClinicalEventType.ORDER,
            ClinicalEventTriggerPoint.MEDICATION_PRESCRIBE,
            ClinicalEventStatus.RECEIVED,
            "PHARMACY_REVIEW");
        when(events.findByEventIdAndTenantId("evt-pharmacy-review", "tenant-A"))
            .thenReturn(Optional.of(event));
        when(payloads.findByEventIdAndTenantId("evt-pharmacy-review", "tenant-A"))
            .thenReturn(Optional.of(payload("evt-pharmacy-review", """
                {
                  "patient": {
                    "mpi": "MPI-1"
                  },
                  "medications": [
                    {
                      "standardCode": "J01C",
                      "codeSystem": "ATC",
                      "localCode": "J01C",
                      "localCodeSystem": "PHARMACY_REVIEW",
                      "sourceSystem": "PHARMACY_REVIEW",
                      "runtimeReleaseId": "runtime-release-test",
                      "mappingId": 3,
                      "standardTermId": 2,
                      "mappedVersion": "V1"
                    }
                  ],
                  "conditions": [
                    {
                      "standardCode": "J18.900",
                      "codeSystem": "ICD-10",
                      "localCode": "J18.900",
                      "localCodeSystem": "PHARMACY_REVIEW",
                      "sourceSystem": "PHARMACY_REVIEW",
                      "runtimeReleaseId": "runtime-release-test",
                      "mappingId": 5,
                      "standardTermId": 3,
                      "mappedVersion": "V1"
                    }
                  ],
	                  "observations": [
	                    {
	                      "code": "PCT",
	                      "valueNumeric": 2.4
	                    }
	                  ],
	                  "extensions": {
	                    "local": {
	                      "existingFlag": "Y",
	                      "sourceTraceId": "pharmacy-review-request-1"
	                    }
	                  },
	                  "pharmacyReview": {
	                    "reviewResult": "REQUIRES_PHYSICIAN_CONFIRMATION",
	                    "pharmacistOpinion": "抗菌药物使用需结合感染指标与病原学复核。"
	                  }
	                }
                """)));

        ClinicalEventStatus status = processor.process("evt-pharmacy-review", "tenant-A");

        assertThat(status).isEqualTo(ClinicalEventStatus.PROCESSED);
        ArgumentCaptor<ContextSnapshotRequest> snapshotCap = ArgumentCaptor.forClass(ContextSnapshotRequest.class);
        verify(contextSnapshots).createBound(
            snapshotCap.capture(), eq("clinical-event:evt-pharmacy-review"), anyString());
        ContextSnapshotResources resources = snapshotCap.getValue().resources();
        assertThat(resources.medications()).singleElement().satisfies(medication -> {
            assertThat(medication.code()).isEqualTo("J01C");
            assertThat(medication.sourceSystem()).isEqualTo("PHARMACY_REVIEW");
        });
        assertThat(resources.conditions()).singleElement().satisfies(condition -> {
            assertThat(condition.code()).isEqualTo("J18.900");
            assertThat(condition.sourceSystem()).isEqualTo("PHARMACY_REVIEW");
        });
        assertThat(resources.observations()).singleElement().satisfies(observation -> {
            assertThat(observation.code()).isEqualTo("PCT");
            assertThat(observation.valueNumeric()).isEqualByComparingTo("2.4");
        });
        assertThat(resources.extensions().at("/local/pharmacyReview/reviewResult").asText())
            .isEqualTo("REQUIRES_PHYSICIAN_CONFIRMATION");
	        assertThat(resources.extensions().at("/local/pharmacyReview/pharmacistOpinion").asText())
	            .isEqualTo("抗菌药物使用需结合感染指标与病原学复核。");
	        assertThat(resources.extensions().at("/local/sourceTraceId").asText())
	            .isEqualTo("pharmacy-review-request-1");
	        assertThat(resources.extensions().at("/local/existingFlag").asText()).isEqualTo("Y");

	        ClinicalEventContext context = ruleAdapter.contexts().get(0);
	        assertThat(context.payload().path("eventPayload").path("pharmacyReview").path("reviewResult").asText())
	            .isEqualTo("REQUIRES_PHYSICIAN_CONFIRMATION");
	        assertThat(context.payload().at("/extensions/local/pharmacyReview/reviewResult").asText())
	            .isEqualTo("REQUIRES_PHYSICIAN_CONFIRMATION");
	        assertThat(context.payload().at("/extensions/local/sourceTraceId").asText())
	            .isEqualTo("pharmacy-review-request-1");
	        assertThat(context.payload().at("/extensions/local/existingFlag").asText()).isEqualTo("Y");
	    }

    @Test
    void processMarksEventFailedWhenAnEngineIsUnavailable() {
        ClinicalEvent event = event(ClinicalEventStatus.RECEIVED);
        when(events.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(event));
        when(payloads.findByEventIdAndTenantId("evt-1", "tenant-A")).thenReturn(Optional.of(payload()));
        processor = new ClinicalEventProcessor(
            events, payloads, auditRecorder, auditPublisher, transitions, applicationEvents,
            new ClinicalEventContextFactory(new ObjectMapper().findAndRegisterModules()),
            new ClinicalEventEngineDispatcher(List.of(
                new UnavailableAdapter(),
                new CapturingAdapter(ClinicalEventEngine.PATHWAY),
                new CapturingAdapter(ClinicalEventEngine.CDSS))),
            contextSnapshots);

        ClinicalEventStatus status = processor.process("evt-1", "tenant-A");

        assertThat(status).isEqualTo(ClinicalEventStatus.FAILED);
        ArgumentCaptor<ClinicalEvent> eventCap = ArgumentCaptor.forClass(ClinicalEvent.class);
        verify(events, org.mockito.Mockito.times(2)).save(eventCap.capture());
        assertThat(eventCap.getAllValues())
            .extracting(ClinicalEvent::processingStatus)
            .containsExactly(ClinicalEventStatus.MAPPED, ClinicalEventStatus.FAILED);
        assertThat(eventCap.getAllValues().get(1).errorCode())
            .isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE.code());
        ArgumentCaptor<TransitionError> errorCap = ArgumentCaptor.forClass(TransitionError.class);
        verify(transitions).record(eq("clinical_event"), eq("evt-1"),
            eq("MAPPED"), eq("FAILED"), eq("ENGINES_UNAVAILABLE"), errorCap.capture());
        assertThat(errorCap.getValue().errorCode()).isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE.code());
        verify(applicationEvents, never()).publishEvent(any(ClinicalEventProcessedEvent.class));
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
        return event("evt-1", ClinicalEventType.DIAGNOSIS, ClinicalEventTriggerPoint.PATIENT_VIEW, status);
    }

    private ClinicalEvent event(String eventId, ClinicalEventType eventType,
                                ClinicalEventTriggerPoint triggerPoint,
                                ClinicalEventStatus status) {
        return event(eventId, eventType, triggerPoint, status, "HIS");
    }

    private ClinicalEvent event(String eventId, ClinicalEventType eventType,
                                ClinicalEventTriggerPoint triggerPoint,
                                ClinicalEventStatus status,
                                String sourceSystem) {
        return new ClinicalEvent(
            1L, eventId, "tenant-A", eventType,
            triggerPoint, null, null,
            "{\"tenantId\":\"tenant-A\",\"hospitalId\":\"hospital-A\",\"departmentId\":\"dept-A\",\"specialtyId\":\"specialty-A\"}",
            "MPI-1", "ENC-1", ClinicalSetting.INPATIENT, sourceSystem, "runtime-release-test", "digest",
            Instant.parse("2026-05-27T01:00:00Z"), Instant.parse("2026-05-27T01:00:01Z"),
            null, status, null, null, 0, null, "trace-1");
    }

    private ClinicalEventPayload payload() {
        return payload("evt-1", "{\"a\":1}");
    }

    private ClinicalEventPayload payload(String eventId, String payload) {
        return new ClinicalEventPayload(
            1L, eventId, "tenant-A", payload, null,
            "INLINE", "application/json", "digest", (long) payload.length(), Instant.now(), null);
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

    private static final class UnavailableAdapter implements ClinicalEventEngineAdapter {
        @Override
        public ClinicalEventEngine engine() {
            return ClinicalEventEngine.RULE;
        }

        @Override
        public ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context) {
            return ClinicalEventEngineDispatchResult.unavailable(
                engine(), null, "规则引擎事件触发求值超时");
        }
    }
}
