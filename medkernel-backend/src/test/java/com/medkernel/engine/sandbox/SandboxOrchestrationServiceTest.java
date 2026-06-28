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
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.embed.EmbedEngineService;
import com.medkernel.engine.embed.EmbedIntegrationMode;
import com.medkernel.engine.embed.EmbedLaunchTokenRequest;
import com.medkernel.engine.embed.EmbedLaunchTokenResponse;
import com.medkernel.engine.evaluation.EvaluationEngineService;
import com.medkernel.engine.evaluation.EvaluationEvaluateSnapshotRequest;
import com.medkernel.engine.evaluation.EvaluationRunResponse;
import com.medkernel.engine.evaluation.EvaluationRunStatus;
import com.medkernel.engine.followup.FollowupEngineService;
import com.medkernel.engine.followup.FollowupPlanDetailResponse;
import com.medkernel.engine.followup.FollowupPlanStatus;
import com.medkernel.engine.pathway.PathwayAdvanceRequest;
import com.medkernel.engine.pathway.PathwayAdvanceResponse;
import com.medkernel.engine.pathway.PathwayEngineService;
import com.medkernel.engine.pathway.PathwayEdgeType;
import com.medkernel.engine.pathway.PathwayEntryMode;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayDetailResponse;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.pathway.PatientPathwayEnterRequest;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationEvaluationResponse;
import com.medkernel.engine.recommendation.RecommendationModelStatus;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.recommendation.RecommendationTriggerStatus;
import com.medkernel.engine.sandbox.compare.SandboxComparableRuleResult;
import com.medkernel.engine.sandbox.compare.SandboxComparisonResponse;
import com.medkernel.engine.sandbox.compare.SandboxComparisonService;
import com.medkernel.engine.sandbox.compare.SandboxComparisonSummary;
import com.medkernel.engine.sandbox.compare.SandboxCurrentRuleExecutor;
import com.medkernel.engine.sandbox.compare.SandboxHistoricalRuleAdapter;
import com.medkernel.engine.sandbox.replay.SandboxReplayRuleExecutor;
import com.medkernel.engine.sandbox.replay.SandboxReplayResolvedCase;
import com.medkernel.engine.sandbox.replay.SandboxReplayCase;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class SandboxOrchestrationServiceTest {

    private final ContextSnapshotService snapshots = mock(ContextSnapshotService.class);
    private final RecommendationEngineService recommendations = mock(RecommendationEngineService.class);
    private final PathwayEngineService pathways = mock(PathwayEngineService.class);
    private final FollowupEngineService followups = mock(FollowupEngineService.class);
    private final EvaluationEngineService evaluations = mock(EvaluationEngineService.class);
    private final EmbedEngineService embed = mock(EmbedEngineService.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final SandboxRuntimeBaselineResolver baselines = mock(SandboxRuntimeBaselineResolver.class);
    private final SandboxRunRepository runs = mock(SandboxRunRepository.class);
    private final SandboxReplayRuleExecutor replayRules = mock(SandboxReplayRuleExecutor.class);
    private final SandboxHistoricalRuleAdapter historicalRules = mock(SandboxHistoricalRuleAdapter.class);
    private final SandboxCurrentRuleExecutor currentRules = mock(SandboxCurrentRuleExecutor.class);
    private final SandboxComparisonService comparisons = mock(SandboxComparisonService.class);
    private final SandboxOrchestrationService service = new SandboxOrchestrationService(
        new SandboxScenarioCatalog(), snapshots, recommendations, pathways, followups, evaluations, embed,
        new ObjectMapper().findAndRegisterModules(), audit, baselines, runs, replayRules,
        historicalRules, currentRules, comparisons);

    @BeforeEach
    void setUpContext() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-sandbox",
            new OrgScope("tenant-1", null, "hospital-1", null, null, "dept-pathway", null, null),
            "doctor-1"));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "doctor-1", null, "ROLE_CLINICAL_USER"));
        when(baselines.resolveCurrent()).thenReturn(runtimeBaseline());
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
        assertThat(response.hookInstance()).isEqualTo("hook-sandbox-x");
        assertThat(response.embedModes()).containsExactly("IFRAME");
        assertThat(response.runId()).isNotBlank();
        assertThat(response.baselineId()).isEqualTo("baseline-runtime-1");
        assertThat(response.mode()).isEqualTo(SandboxRunMode.CURRENT);
        assertThat(response.runtimeReleaseRef()).isEqualTo("runtime-release-test");
        assertThat(response.runtimeRevisionNo()).isEqualTo(7L);
        assertThat(response.resolutionSource())
            .isEqualTo(SandboxResolutionSource.CURRENT_RUNTIME_RELEASE);
        assertThat(response.externalSideEffects()).isFalse();

        ArgumentCaptor<ContextSnapshotRequest> snapshotCaptor =
            ArgumentCaptor.forClass(ContextSnapshotRequest.class);
        ArgumentCaptor<String> releaseIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(snapshots).createBound(
            snapshotCaptor.capture(), anyString(), releaseIdCaptor.capture());
        assertThat(releaseIdCaptor.getValue()).isEqualTo("runtime-release-test");
        ArgumentCaptor<RecommendationTriggerRequest> recommendationCaptor =
            ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(recommendations).evaluate(recommendationCaptor.capture());
        verify(baselines, times(1)).resolveCurrent();

        InOrder calls = inOrder(snapshots, recommendations, embed);
        calls.verify(snapshots).createBound(any(), anyString(), anyString());
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
        when(snapshots.createBound(any(), anyString(), anyString()))
            .thenReturn(new ContextSnapshotResponse(
            "ctx-x", ContextSnapshotStatus.ACTIVE, null, "runtime-release-test", QualityStatus.VALID,
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
    void runtimeBaselineFailureIsRecordedBeforeAnyDomainServiceCall() {
        when(baselines.resolveCurrent())
            .thenThrow(new IllegalStateException("SANDBOX_RUNTIME_BASELINE_MISSING"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.run(
            "sbx-med-warfarin-asa", request()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SANDBOX_RUNTIME_BASELINE_MISSING");

        ArgumentCaptor<SandboxRun> runCaptor = ArgumentCaptor.forClass(SandboxRun.class);
        verify(runs, times(2)).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().get(0).status()).isEqualTo(SandboxRunStatus.PREPARING);
        assertThat(runCaptor.getAllValues().get(1).status()).isEqualTo(SandboxRunStatus.FAILED);
        assertThat(runCaptor.getAllValues().get(1).failureMessage())
            .contains("SANDBOX_RUNTIME_BASELINE_MISSING");
        verifyNoInteractions(snapshots, recommendations, pathways, followups, evaluations, embed, audit);
    }

    @Test
    void historicalExactUsesReplayManifestAndSuppressesEveryWritableDomainService() {
        SandboxRuntimeBaseline historical = historicalBaseline();
        when(baselines.resolveHistorical("replay-1"))
            .thenReturn(historical);
        when(replayRules.execute(historical.historicalReplay())).thenReturn(List.of());

        SandboxRunResponse response = service.run(
            "sbx-lab-critical-k",
            new SandboxRunRequest(
                "SNAPSHOT", null, null, null, EmbedIntegrationMode.IFRAME,
                SandboxRunMode.HISTORICAL_EXACT, "replay-1"));

        assertThat(response.result()).isEqualTo("PASS");
        assertThat(response.mode()).isEqualTo(SandboxRunMode.HISTORICAL_EXACT);
        assertThat(response.replayCaseId()).isEqualTo("replay-1");
        assertThat(response.resolutionSource()).isEqualTo(SandboxResolutionSource.REPLAY_MANIFEST);
        assertThat(response.steps()).extracting(SandboxStepTrace::stage)
            .containsExactly("REPLAY_MANIFEST", "HISTORICAL_RULES");
        assertThat(response.snapshotId()).isNull();
        assertThat(response.embedToken()).isNull();
        assertThat(response.externalSideEffects()).isFalse();
        verify(baselines).resolveHistorical("replay-1");
        verify(replayRules).execute(historical.historicalReplay());
        verifyNoInteractions(snapshots, recommendations, pathways, followups, evaluations, embed);
    }

    @Test
    void compareExecutesBothFrozenRuleSetsOnTheSameReplayContextWithoutDomainSideEffects() {
        SandboxRuntimeBaseline baseline = compareBaseline();
        List<SandboxComparableRuleResult> historical = List.of();
        List<SandboxComparableRuleResult> current = List.of();
        SandboxComparisonResponse comparison = new SandboxComparisonResponse(
            "context-hash", new SandboxComparisonSummary(0, 0, 0, 0, 0), List.of(), 0);
        when(baselines.resolveCompare("replay-1"))
            .thenReturn(baseline);
        when(historicalRules.execute(baseline.historicalReplay())).thenReturn(historical);
        when(currentRules.execute(
            baseline.runtimeContent(), baseline.historicalReplay().contextSnapshot()))
            .thenReturn(current);
        when(comparisons.compare("context-hash", historical, current)).thenReturn(comparison);

        SandboxRunResponse response = service.run(
            "sbx-lab-critical-k",
            new SandboxRunRequest(
                "SNAPSHOT", null, null, null, EmbedIntegrationMode.IFRAME,
                SandboxRunMode.COMPARE, "replay-1"));

        assertThat(response.result()).isEqualTo("PASS");
        assertThat(response.mode()).isEqualTo(SandboxRunMode.COMPARE);
        assertThat(response.comparison()).isSameAs(comparison);
        assertThat(response.steps()).extracting(SandboxStepTrace::stage)
            .containsExactly("REPLAY_MANIFEST", "HISTORICAL_RULES", "CURRENT_RULES", "COMPARISON");
        verify(historicalRules).execute(baseline.historicalReplay());
        verify(currentRules).execute(
            baseline.runtimeContent(), baseline.historicalReplay().contextSnapshot());
        verify(comparisons).compare("context-hash", historical, current);
        verifyNoInteractions(snapshots, recommendations, pathways, followups, evaluations, embed);
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

        SandboxRunResponse response = service.run("sbx-pathway-cycle", request());

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
        ArgumentCaptor<PatientPathwayEnterRequest> enterCaptor =
            ArgumentCaptor.forClass(PatientPathwayEnterRequest.class);
        verify(pathways).enterPatientPathway(enterCaptor.capture());
        assertThat(enterCaptor.getValue().contextSnapshotId()).isEqualTo("ctx-x");
        ArgumentCaptor<PathwayAdvanceRequest> advanceCaptor =
            ArgumentCaptor.forClass(PathwayAdvanceRequest.class);
        verify(pathways).advance(advanceCaptor.capture());
        assertThat(advanceCaptor.getValue().eventId())
            .startsWith("sandbox:sbx-pathway-cycle:")
            .endsWith(":advance")
            .hasSizeLessThanOrEqualTo(64);
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
        ArgumentCaptor<EvaluationEvaluateSnapshotRequest> evaluationCaptor =
            ArgumentCaptor.forClass(EvaluationEvaluateSnapshotRequest.class);
        verify(evaluations).evaluateSnapshot(evaluationCaptor.capture());
        assertThat(evaluationCaptor.getValue().contextSnapshotId()).isEqualTo("ctx-x");
        assertThat(evaluationCaptor.getValue().scenarioCode()).isEqualTo("sbx-evaluation-closed-loop");
    }

    @Test
    void compositeRecommendationCarriesTraceableSuggestOrderCandidate() {
        stubCommonChain();

        SandboxRunResponse response = service.run("sbx-recommendation-composite", request());

        ArgumentCaptor<RecommendationTriggerRequest> requestCaptor =
            ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(recommendations).evaluate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().triggerCode())
            .matches("sandbox:sbx-recommendation-composite:[0-9a-f]{12}")
            .doesNotContain("trace-sandbox");
        assertThat(requestCaptor.getValue().sourceEventId())
            .matches("sandbox-event:sbx-recommendation-composite:[0-9a-f]{12}")
            .doesNotContain("trace-sandbox")
            .hasSizeLessThanOrEqualTo(64);
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
        when(snapshots.createBound(any(), anyString(), anyString()))
            .thenReturn(new ContextSnapshotResponse(
            "ctx-x", ContextSnapshotStatus.ACTIVE, null, "runtime-release-test", QualityStatus.VALID,
            List.of(), Map.of(), Instant.now(), "trace-sandbox"));
        when(recommendations.evaluate(any())).thenReturn(new RecommendationEvaluationResponse(
            "trigger-x", RecommendationTriggerStatus.EVALUATED, 1, 1, 0,
            RecommendationModelStatus.MODEL_DISABLED, List.of(), "trace-sandbox"));
        when(embed.generateToken(any())).thenReturn(new EmbedLaunchTokenResponse(
            "token-x", Instant.now().plusSeconds(300), "/embed/launch?token=token-x",
            EmbedIntegrationMode.IFRAME, "/api/v1/engine/embed/launch", "result-review",
            "hook-sandbox-x"));
    }

    private static SandboxRunRequest request() {
        return new SandboxRunRequest(
            "SNAPSHOT", null, Instant.parse("2026-06-14T00:00:00Z"),
            "https://his.hospital.com");
    }

    private static SandboxRuntimeBaseline runtimeBaseline() {
        return new SandboxRuntimeBaseline(
            "baseline-runtime-1",
            SandboxRunMode.CURRENT,
            "tenant-1",
            "dept-pathway",
            "runtime-release-test",
            7L,
            "platform-baseline-3",
            "a".repeat(64),
            SandboxResolutionSource.CURRENT_RUNTIME_RELEASE,
            Instant.parse("2026-06-19T00:00:00Z"),
            runtimeContent(),
            null,
            null);
    }

    private static SandboxRuntimeBaseline historicalBaseline() {
        var replay = mock(SandboxReplayResolvedCase.class);
        var replayCase = mock(SandboxReplayCase.class);
        when(replay.replayCase()).thenReturn(replayCase);
        when(replay.assets()).thenReturn(List.of());
        when(replayCase.deidentificationProfile()).thenReturn("MEDKERNEL_D4_STRICT_V1");
        when(replayCase.manifestHash()).thenReturn("a".repeat(64));
        when(replayCase.sourceRuntimeReleaseRef()).thenReturn("sha256:" + "6".repeat(64));
        when(replayCase.sourceRuntimeRevisionNo()).thenReturn(4L);
        return new SandboxRuntimeBaseline(
            "baseline-history-1", SandboxRunMode.HISTORICAL_EXACT, "tenant-1", "dept-pathway",
            null, 4L, null, "a".repeat(64), SandboxResolutionSource.REPLAY_MANIFEST,
            Instant.parse("2026-06-19T00:00:00Z"), null, "replay-1", replay);
    }

    private static SandboxRuntimeBaseline compareBaseline() {
        SandboxRuntimeBaseline historical = historicalBaseline();
        var replay = historical.historicalReplay();
        var context = new ObjectMapper().createObjectNode();
        when(replay.contextSnapshot()).thenReturn(context);
        when(replay.replayCase().contextSnapshotHash()).thenReturn("context-hash");
        SandboxRuntimeBaseline current = runtimeBaseline();
        return new SandboxRuntimeBaseline(
            "baseline-compare-1", SandboxRunMode.COMPARE, current.tenantId(),
            current.targetOrgUnitId(), current.runtimeReleaseId(), current.runtimeRevisionNo(),
            current.platformBaselineReleaseId(), current.manifestSha256(),
            current.resolutionSource(), Instant.parse("2026-06-19T00:00:00Z"),
            current.runtimeContent(), "replay-1", replay);
    }

    private static ClinicalRuntimeReleaseContent runtimeContent() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        ClinicalRuntimeRelease release = new ClinicalRuntimeRelease(
            null, "runtime-release-test", "tenant-1", "hospital-1", 7L,
            "platform-baseline-3", "a".repeat(64), null, now, "governor-1",
            now, "governor-1", "trace-sandbox");
        return new ClinicalRuntimeReleaseContent(release, List.of());
    }

    private static PathwayTemplate pathwayTemplate() {
        return new PathwayTemplate(
            null, "pt-sandbox-cycle", "tenant-1", "PATH.CLINICAL.CYCLE",
            "基础节点闭环", null, 1,
            PathwayTemplateLevel.HOSPITAL, PathwayTemplateStatus.PUBLISHED,
            PathwayEntryMode.MANUAL_CONFIRM, "ASSESS", "sandbox", "沙盘路径",
            null, null, Instant.now(), "tester", Instant.now(), "tester", "trace-sandbox");
    }

    private static PatientPathwayDetailResponse pathwayDetail() {
        Instant now = Instant.now();
        PatientPathway runtime = new PatientPathway(
            null, "pp-sandbox-1", "tenant-1", "SBX-LAB-K-001", "SBX-LAB-K-ENC-001",
            "pt-sandbox-cycle", "release-H1", "av-pathway-v1",
            "ASSESS", PatientPathwayStatus.NODE_EXECUTING,
            now, null, null, null, null, now, "tester", now, "tester", "trace-sandbox");
        return new PatientPathwayDetailResponse(
            runtime, List.of(), List.of(), List.of(), List.of(), List.of(), "trace-sandbox");
    }
}
