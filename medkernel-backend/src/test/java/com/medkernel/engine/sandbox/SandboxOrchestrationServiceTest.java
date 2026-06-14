package com.medkernel.engine.sandbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.embed.EmbedEngineService;
import com.medkernel.engine.embed.EmbedIntegrationMode;
import com.medkernel.engine.embed.EmbedLaunchTokenRequest;
import com.medkernel.engine.embed.EmbedLaunchTokenResponse;
import com.medkernel.engine.evaluation.EvaluationEngineService;
import com.medkernel.engine.evaluation.EvaluationRunResponse;
import com.medkernel.engine.evaluation.EvaluationRunStatus;
import com.medkernel.engine.followup.FollowupEngineService;
import com.medkernel.engine.followup.FollowupPlanDetailResponse;
import com.medkernel.engine.followup.FollowupPlanStatus;
import com.medkernel.engine.pathway.PathwayEngineService;
import com.medkernel.engine.pathway.PathwayAdvanceResponse;
import com.medkernel.engine.pathway.PathwayEdgeType;
import com.medkernel.engine.pathway.PathwayEntryMode;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayDetailResponse;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationEvaluationResponse;
import com.medkernel.engine.recommendation.RecommendationModelStatus;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.recommendation.RecommendationTriggerStatus;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SandboxOrchestrationServiceTest {

    private final ContextSnapshotService snapshots = mock(ContextSnapshotService.class);
    private final RecommendationEngineService recommendations = mock(RecommendationEngineService.class);
    private final PathwayEngineService pathways = mock(PathwayEngineService.class);
    private final FollowupEngineService followups = mock(FollowupEngineService.class);
    private final EvaluationEngineService evaluations = mock(EvaluationEngineService.class);
    private final EmbedEngineService embed = mock(EmbedEngineService.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final SandboxOrchestrationService service = new SandboxOrchestrationService(
        new SandboxScenarioCatalog(), snapshots, recommendations, pathways, followups, evaluations, embed,
        new ObjectMapper().findAndRegisterModules(), audit);

    @BeforeEach
    void setUpContext() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-sandbox",
            new OrgScope("tenant-1", null, "hospital-1", null, null, "dept-ed", null, null),
            "doctor-1"));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "doctor-1", null, "ROLE_CLINICAL_DECISION_USER"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void runHappyPathProducesThreeOkStepsInOrderAndAggregatesResult() {
        stubCommonChain();

        SandboxRunResponse response = service.run(
            "sbx-lab-critical-k",
            request());

        assertThat(response.result()).isEqualTo("PASS");
        assertThat(response.steps()).extracting(SandboxStepTrace::stage)
            .containsExactly("CONTEXT", "RECOMMENDATION", "TOKEN");
        assertThat(response.steps()).allMatch(step -> "OK".equals(step.status()));
        assertThat(response.snapshotId()).isEqualTo("ctx-x");
        assertThat(response.triggerId()).isEqualTo("trigger-x");
        assertThat(response.cardCount()).isEqualTo(1);
        assertThat(response.embedToken()).isEqualTo("token-x");
        assertThat(response.embedUrl()).isEqualTo("/embed/launch?token=token-x");
        assertThat(response.embedModes()).containsExactly("IFRAME");

        InOrder calls = inOrder(snapshots, recommendations, embed);
        calls.verify(snapshots).create(any(), anyString());
        calls.verify(recommendations).evaluate(any());
        calls.verify(embed).generateToken(any());
        verify(audit).record(
            AuditAction.EXECUTE,
            "sandbox_scenario",
            "sbx-lab-critical-k",
            "沙盘编排结果=PASS steps=3 cardCount=1");
    }

    @Test
    void recommendationFailureShortCircuitsAndRetainsCompletedSnapshotEvidence() {
        when(snapshots.create(any(), anyString())).thenReturn(new ContextSnapshotResponse(
            "ctx-x", ContextSnapshotStatus.ACTIVE, null, "2026.06.1", QualityStatus.VALID,
            List.of(), Map.of(), Instant.now(), "trace-sandbox"));
        when(recommendations.evaluate(any())).thenThrow(new IllegalStateException("规则资产未发布"));

        SandboxRunResponse response = service.run(
            "sbx-lab-critical-k",
            new SandboxRunRequest(
                "SNAPSHOT", null, Instant.parse("2026-06-14T00:00:00Z"), "https://his.hospital.com"));

        assertThat(response.result()).isEqualTo("FAIL");
        assertThat(response.snapshotId()).isEqualTo("ctx-x");
        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().get(1).status()).isEqualTo("FAIL");
        assertThat(response.steps().get(1).error()).contains("规则资产未发布");
        verifyNoInteractions(embed);
        verify(audit).record(
            AuditAction.EXECUTE,
            "sandbox_scenario",
            "sbx-lab-critical-k",
            "沙盘编排结果=FAIL steps=2 cardCount=0");
    }

    @Test
    void clinicalReviewScenarioIsRejectedBeforeAnyEngineSideEffect() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.run(
                "sbx-med-warfarin-asa",
                new SandboxRunRequest(
                    "SNAPSHOT", null, Instant.parse("2026-06-14T00:00:00Z"),
                    "https://his.hospital.com")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("临床评审");

        verifyNoInteractions(snapshots, recommendations, pathways, followups, evaluations, embed, audit);
    }

    @Test
    void pathwayPlaybookEntersPublishedTemplateAndReturnsRuntimeIdentity() {
        stubCommonChain();
        PathwayTemplate template = pathwayTemplate();
        when(pathways.listTemplates(any(), any())).thenReturn(
            PageResponse.of(List.of(template), PageRequest.defaults(), 1));
        when(pathways.enterPatientPathway(any())).thenReturn(pathwayDetail());
        when(pathways.advance(any())).thenReturn(new PathwayAdvanceResponse(
            "pp-sandbox-1",
            "ASSESS",
            "OBSERVE",
            PatientPathwayStatus.NODE_EXECUTING,
            null,
            "EDGE.ASSESS.OBSERVE",
            PathwayEdgeType.DEFAULT,
            "ctx-x",
            QualityStatus.VALID,
            List.of(),
            Map.of(),
            Map.of(),
            Map.of("selection", "DEFAULT"),
            null,
            0,
            null,
            List.of(),
            List.of(),
            "trace-sandbox"));

        SandboxRunResponse response = service.run("sbx-pathway-ed", request());

        assertThat(response.result()).isEqualTo("PASS");
        assertThat(response.steps()).extracting(SandboxStepTrace::stage)
            .containsExactly(
                "CONTEXT", "PATHWAY_ENTER", "PATHWAY_ADVANCE", "RECOMMENDATION", "TOKEN");
        assertThat(response.steps().get(2).serverFacts())
            .containsEntry("previousNodeCode", "ASSESS")
            .containsEntry("nextNodeCode", "OBSERVE")
            .containsEntry("edgeCode", "EDGE.ASSESS.OBSERVE");
        assertThat(response.patientPathwayId()).isEqualTo("pp-sandbox-1");
        verify(pathways).listTemplates(any(), any());
        verify(pathways).enterPatientPathway(any());
        verify(pathways).advance(any());
    }

    @Test
    void followupPlaybookGeneratesControlledPlanAndReturnsPlanIdentity() {
        stubCommonChain();
        when(followups.generatePlan(any())).thenReturn(new FollowupPlanDetailResponse(
            "fp-sandbox-1", "tenant-1", "SBX-FU-001", "SBX-FU-ENC-001", null,
            FollowupPlanStatus.ACTIVE, List.of()));

        SandboxRunResponse response = service.run("sbx-followup-closed-loop", request());

        assertThat(response.result()).isEqualTo("PASS");
        assertThat(response.steps()).extracting(SandboxStepTrace::stage)
            .containsExactly("CONTEXT", "FOLLOWUP", "RECOMMENDATION", "TOKEN");
        assertThat(response.followupPlanId()).isEqualTo("fp-sandbox-1");
        verify(followups).generatePlan(any());
    }

    @Test
    void evaluationPlaybookRunsSnapshotEvaluationAndReturnsRunIdentity() {
        stubCommonChain();
        when(evaluations.evaluateSnapshot(any())).thenReturn(new EvaluationRunResponse(
            "er-sandbox-1", EvaluationRunStatus.RECORDED, 1, 1, 1, "trace-sandbox"));

        SandboxRunResponse response = service.run("sbx-evaluation-closed-loop", request());

        assertThat(response.result()).isEqualTo("PASS");
        assertThat(response.steps()).extracting(SandboxStepTrace::stage)
            .containsExactly("CONTEXT", "EVALUATION", "RECOMMENDATION", "TOKEN");
        assertThat(response.evaluationRunId()).isEqualTo("er-sandbox-1");
        verify(evaluations).evaluateSnapshot(any());
    }

    @Test
    void compositeRecommendationCarriesTraceableSuggestOrderCandidate() {
        stubCommonChain();

        SandboxRunResponse response = service.run("sbx-recommendation-composite", request());

        ArgumentCaptor<RecommendationTriggerRequest> requestCaptor =
            ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(recommendations).evaluate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().triggerCode())
            .isEqualTo("sandbox:sbx-recommendation-composite:trace-sandbox");
        assertThat(requestCaptor.getValue().sourceEventId())
            .isEqualTo("sandbox-event:sbx-recommendation-composite:trace-sandbox");
        assertThat(requestCaptor.getValue().candidateCards()).singleElement().satisfies(card -> {
            assertThat(card.suggestedAction()).isEqualTo("SUGGEST_ORDER");
            assertThat(card.requiresPhysicianConfirmation()).isTrue();
            assertThat(card.sources()).isNotEmpty();
        });
        assertThat(response.result()).isEqualTo("PASS");
    }

    @Test
    void embedPlaybookDeclaresAllSupportedIntegrationModes() {
        stubCommonChain();

        SandboxRunResponse response = service.run(
            "sbx-embed-modes",
            new SandboxRunRequest(
                "SNAPSHOT",
                null,
                Instant.parse("2026-06-14T00:00:00Z"),
                "https://his.hospital.com",
                EmbedIntegrationMode.SDK));

        assertThat(response.result()).isEqualTo("PASS");
        assertThat(response.embedModes()).containsExactly("IFRAME", "SDK", "API");
        ArgumentCaptor<EmbedLaunchTokenRequest> tokenCaptor =
            ArgumentCaptor.forClass(EmbedLaunchTokenRequest.class);
        verify(embed).generateToken(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().integrationMode()).isEqualTo(EmbedIntegrationMode.SDK);
    }

    private void stubCommonChain() {
        when(snapshots.create(any(), anyString())).thenReturn(new ContextSnapshotResponse(
            "ctx-x", ContextSnapshotStatus.ACTIVE, null, "2026.06.1", QualityStatus.VALID,
            List.of(), Map.of(), Instant.now(), "trace-sandbox"));
        when(recommendations.evaluate(any())).thenReturn(new RecommendationEvaluationResponse(
            "trigger-x", RecommendationTriggerStatus.EVALUATED, 1, 1, 0,
            RecommendationModelStatus.MODEL_DISABLED, List.of(), "trace-sandbox"));
        when(embed.generateToken(any())).thenReturn(new EmbedLaunchTokenResponse(
            "token-x", Instant.now().plusSeconds(300), "/embed/launch?token=token-x",
            EmbedIntegrationMode.IFRAME, "/api/v1/engine/embed/launch", "result-review"));
    }

    private static SandboxRunRequest request() {
        return new SandboxRunRequest(
            "SNAPSHOT", null, Instant.parse("2026-06-14T00:00:00Z"),
            "https://his.hospital.com");
    }

    private static PathwayTemplate pathwayTemplate() {
        return new PathwayTemplate(
            null, "pt-sandbox-ed", "tenant-1", "pkg-pathway-ed",
            "PATH.ED.DISPOSITION", "急诊处置路径", null, 1,
            PathwayTemplateLevel.HOSPITAL, PathwayTemplateStatus.PUBLISHED,
            PathwayEntryMode.MANUAL_CONFIRM, "ASSESS", "sandbox", "沙盘路径",
            null, null, Instant.now(), "tester", Instant.now(), "tester", "trace-sandbox");
    }

    private static PatientPathwayDetailResponse pathwayDetail() {
        Instant now = Instant.now();
        PatientPathway runtime = new PatientPathway(
            null, "pp-sandbox-1", "tenant-1", "SBX-LAB-K-001", "SBX-LAB-K-ENC-001",
            "pt-sandbox-ed", "ASSESS", PatientPathwayStatus.NODE_EXECUTING,
            now, null, null, null, null, now, "tester", now, "tester", "trace-sandbox");
        return new PatientPathwayDetailResponse(
            runtime, List.of(), List.of(), List.of(), List.of(), List.of(), "trace-sandbox");
    }
}
