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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pathway.PathwayEngineService;
import com.medkernel.engine.pathway.PathwayEventDispatchResponse;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationEvaluationResponse;
import com.medkernel.engine.recommendation.RecommendationModelStatus;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.recommendation.RecommendationTriggerStatus;
import com.medkernel.engine.rule.RuleEngineService;
import com.medkernel.engine.rule.RuleEvaluateResponse;
import com.medkernel.shared.context.OrgScope;

class ClinicalEventEngineAdapterTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void ruleAdapterCallsRuleEngineWithSameEventContext() {
        RuleEngineService service = mock(RuleEngineService.class);
        when(service.evaluateContext(any(String.class), any(JsonNode.class), any(String.class), any(List.class)))
            .thenReturn(new RuleEvaluateResponse("eval-1", List.of(), null, "trace-1"));
        var adapter = new ClinicalEventRuleEngineAdapter(service, json);

        ClinicalEventEngineDispatchResult result = adapter.dispatch(context());

        assertThat(result.engine()).isEqualTo(ClinicalEventEngine.RULE);
        assertThat(result.status()).isEqualTo(ClinicalEventEngineDispatchStatus.DISPATCHED);
        assertThat(result.downstreamReferenceId()).isEqualTo("eval-1");
        ArgumentCaptor<String> triggerCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> contextCap = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<String> eventCap = ArgumentCaptor.forClass(String.class);
        verify(service).evaluateContext(triggerCap.capture(), contextCap.capture(), eventCap.capture(), any());
        assertThat(eventCap.getValue()).isEqualTo("evt-1");
        assertThat(triggerCap.getValue()).isEqualTo("patient-view");
        assertThat(contextCap.getValue().path("event").path("eventId").asText()).isEqualTo("evt-1");
        assertThat(contextCap.getValue().path("event").path("triggerPoint").asText()).isEqualTo("patient-view");
        assertThat(contextCap.getValue().path("patient").path("patientId").asText()).isEqualTo("MPI-1");
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
    void cdssAdapterEvaluatesDeterministicRecommendationsFromClinicalEvent() {
        RecommendationEngineService service = mock(RecommendationEngineService.class);
        when(service.evaluate(any(RecommendationTriggerRequest.class)))
            .thenReturn(new RecommendationEvaluationResponse(
                "rt-1", RecommendationTriggerStatus.EVALUATED, 1, 1, 0,
                RecommendationModelStatus.MODEL_DISABLED, List.of(), "trace-1"));
        var adapter = new ClinicalEventRecommendationEngineAdapter(service);

        ClinicalEventEngineDispatchResult result = adapter.dispatch(context());

        assertThat(result.engine()).isEqualTo(ClinicalEventEngine.CDSS);
        assertThat(result.downstreamReferenceId()).isEqualTo("rt-1");
        ArgumentCaptor<RecommendationTriggerRequest> requestCap =
            ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(service).evaluate(requestCap.capture());
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
