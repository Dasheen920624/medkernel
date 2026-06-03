package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pathway.PathwayEngineService;
import com.medkernel.engine.pathway.PathwayEventDispatchResponse;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.recommendation.RecommendationTriggerResponse;
import com.medkernel.engine.recommendation.RecommendationTriggerStatus;
import com.medkernel.engine.rule.RuleEngineService;
import com.medkernel.engine.rule.RuleEvaluateRequest;
import com.medkernel.engine.rule.RuleEvaluateResponse;
import com.medkernel.shared.context.OrgScope;

class ClinicalEventEngineAdapterTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void ruleAdapterCallsRuleEngineWithSameEventContext() {
        RuleEngineService service = mock(RuleEngineService.class);
        when(service.evaluate(any(RuleEvaluateRequest.class)))
            .thenReturn(new RuleEvaluateResponse("eval-1", List.of(), null, "trace-1"));
        var adapter = new ClinicalEventRuleEngineAdapter(service, json);

        ClinicalEventEngineDispatchResult result = adapter.dispatch(context());

        assertThat(result.engine()).isEqualTo(ClinicalEventEngine.RULE);
        assertThat(result.status()).isEqualTo(ClinicalEventEngineDispatchStatus.DISPATCHED);
        assertThat(result.downstreamReferenceId()).isEqualTo("eval-1");
        ArgumentCaptor<RuleEvaluateRequest> requestCap = ArgumentCaptor.forClass(RuleEvaluateRequest.class);
        verify(service).evaluate(requestCap.capture());
        assertThat(requestCap.getValue().eventId()).isEqualTo("evt-1");
        assertThat(requestCap.getValue().triggerPoint()).isEqualTo("patient-view");
        assertThat(requestCap.getValue().context().path("event").path("eventId").asText()).isEqualTo("evt-1");
        assertThat(requestCap.getValue().context().path("event").path("triggerPoint").asText()).isEqualTo("patient-view");
        assertThat(requestCap.getValue().context().path("patient").path("patientId").asText()).isEqualTo("MPI-1");
    }

    @Test
    void pathwayAdapterCallsPathwayEntryWithSameContext() {
        PathwayEngineService service = mock(PathwayEngineService.class);
        when(service.dispatchClinicalEvent(any(ClinicalEventContext.class)))
            .thenReturn(new PathwayEventDispatchResponse("evt-1", "MPI-1", "ENC-1", "trace-1"));
        var adapter = new ClinicalEventPathwayEngineAdapter(service);

        ClinicalEventEngineDispatchResult result = adapter.dispatch(context());

        assertThat(result.engine()).isEqualTo(ClinicalEventEngine.PATHWAY);
        assertThat(result.downstreamReferenceId()).isEqualTo("evt-1");
        ArgumentCaptor<ClinicalEventContext> contextCap = ArgumentCaptor.forClass(ClinicalEventContext.class);
        verify(service).dispatchClinicalEvent(contextCap.capture());
        assertThat(contextCap.getValue().eventId()).isEqualTo("evt-1");
        assertThat(contextCap.getValue().traceId()).isEqualTo("trace-1");
    }

    @Test
    void cdssAdapterCreatesNoCardRecommendationTriggerFromClinicalEvent() {
        RecommendationEngineService service = mock(RecommendationEngineService.class);
        when(service.trigger(any(RecommendationTriggerRequest.class)))
            .thenReturn(new RecommendationTriggerResponse("rt-1", RecommendationTriggerStatus.NO_CARD, 0, "trace-1"));
        var adapter = new ClinicalEventRecommendationEngineAdapter(service);

        ClinicalEventEngineDispatchResult result = adapter.dispatch(context());

        assertThat(result.engine()).isEqualTo(ClinicalEventEngine.CDSS);
        assertThat(result.downstreamReferenceId()).isEqualTo("rt-1");
        ArgumentCaptor<RecommendationTriggerRequest> requestCap =
            ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(service).trigger(requestCap.capture());
        assertThat(requestCap.getValue().sourceEventId()).isEqualTo("evt-1");
        assertThat(requestCap.getValue().patientId()).isEqualTo("MPI-1");
        assertThat(requestCap.getValue().encounterId()).isEqualTo("ENC-1");
        assertThat(requestCap.getValue().scenarioCode()).isEqualTo("patient-view");
        assertThat(requestCap.getValue().inputDigest()).isEqualTo("sha256:payload");
        assertThat(requestCap.getValue().candidateCards()).isEmpty();
    }

    private ClinicalEventContext context() {
        return new ClinicalEventContext(
            "evt-1",
            "tenant-A",
            new OrgScope("tenant-A", "group-A", "hospital-A", "campus-A", "site-A", "dept-A", "specialty-A"),
            ClinicalEventType.DIAGNOSIS,
            ClinicalEventTriggerPoint.PATIENT_VIEW,
            "MPI-1",
            "ENC-1",
            "ctx-1",
            "HIS",
            "pkg-2026.06",
            "sha256:payload",
            Instant.parse("2026-06-01T01:00:00Z"),
            "HIS:patient-view",
            "trace-1",
            json.createObjectNode().put("diagnosisCode", "I10"),
            List.of());
    }
}
