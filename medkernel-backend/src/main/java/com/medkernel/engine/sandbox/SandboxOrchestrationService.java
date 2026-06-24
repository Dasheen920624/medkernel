package com.medkernel.engine.sandbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.embed.EmbedEngineService;
import com.medkernel.engine.embed.EmbedLaunchTokenRequest;
import com.medkernel.engine.embed.EmbedLaunchTokenResponse;
import com.medkernel.engine.evaluation.EvaluationEngineService;
import com.medkernel.engine.evaluation.EvaluationEvaluateSnapshotRequest;
import com.medkernel.engine.evaluation.EvaluationRunResponse;
import com.medkernel.engine.followup.FollowupEngineService;
import com.medkernel.engine.followup.FollowupPlanDetailResponse;
import com.medkernel.engine.followup.FollowupPlanGenerateRequest;
import com.medkernel.engine.pathway.PathwayAdvanceEventType;
import com.medkernel.engine.pathway.PathwayAdvanceRequest;
import com.medkernel.engine.pathway.PathwayAdvanceResponse;
import com.medkernel.engine.pathway.PathwayEngineService;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateFilter;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
import com.medkernel.engine.pathway.PatientPathwayDetailResponse;
import com.medkernel.engine.pathway.PatientPathwayEnterRequest;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationEvaluationResponse;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.sandbox.compare.SandboxComparableRuleResult;
import com.medkernel.engine.sandbox.compare.SandboxComparisonResponse;
import com.medkernel.engine.sandbox.compare.SandboxComparisonService;
import com.medkernel.engine.sandbox.compare.SandboxCurrentRuleExecutor;
import com.medkernel.engine.sandbox.compare.SandboxHistoricalRuleAdapter;
import com.medkernel.engine.sandbox.replay.SandboxReplayRuleExecutor;
import com.medkernel.engine.sandbox.replay.SandboxReplayRuleResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 沙盘编排服务。进程内顺序复用上下文、推荐和嵌入引擎，不复制业务判断。
 */
@Service
public class SandboxOrchestrationService {

    private static final String CONTEXT_ENDPOINT = "/api/v1/engine/context/snapshots";
    private static final String PATHWAY_ENTER_ENDPOINT =
        "/api/v1/engine/pathway/patient-pathways/enter";
    private static final String PATHWAY_ADVANCE_ENDPOINT =
        "/api/v1/engine/pathway/patient-pathways/{patientPathwayId}/advance";
    private static final String FOLLOWUP_ENDPOINT =
        "/api/v1/engine/followup/plans/generate";
    private static final String EVALUATION_ENDPOINT =
        "/api/v1/engine/evaluation/snapshots:evaluate";
    private static final String RECOMMENDATION_ENDPOINT = "/api/v1/engine/recommendations/evaluate";
    private static final String TOKEN_ENDPOINT = "/api/v1/engine/embed/launch-tokens";

    private final SandboxScenarioCatalog catalog;
    private final ContextSnapshotService snapshots;
    private final RecommendationEngineService recommendations;
    private final PathwayEngineService pathways;
    private final FollowupEngineService followups;
    private final EvaluationEngineService evaluations;
    private final EmbedEngineService embed;
    private final ObjectMapper json;
    private final AuditRecorder audit;
    private final SandboxRuntimeBaselineResolver baselines;
    private final SandboxRunRepository runs;
    private final SandboxReplayRuleExecutor replayRules;
    private final SandboxHistoricalRuleAdapter historicalRules;
    private final SandboxCurrentRuleExecutor currentRules;
    private final SandboxComparisonService comparisons;

    public SandboxOrchestrationService(
            SandboxScenarioCatalog catalog,
            ContextSnapshotService snapshots,
            RecommendationEngineService recommendations,
            PathwayEngineService pathways,
            FollowupEngineService followups,
            EvaluationEngineService evaluations,
            EmbedEngineService embed,
            ObjectMapper json,
            AuditRecorder audit,
            SandboxRuntimeBaselineResolver baselines,
            SandboxRunRepository runs,
            SandboxReplayRuleExecutor replayRules,
            SandboxHistoricalRuleAdapter historicalRules,
            SandboxCurrentRuleExecutor currentRules,
            SandboxComparisonService comparisons) {
        this.catalog = catalog;
        this.snapshots = snapshots;
        this.recommendations = recommendations;
        this.pathways = pathways;
        this.followups = followups;
        this.evaluations = evaluations;
        this.embed = embed;
        this.json = json;
        this.audit = audit;
        this.baselines = baselines;
        this.runs = runs;
        this.replayRules = replayRules;
        this.historicalRules = historicalRules;
        this.currentRules = currentRules;
        this.comparisons = comparisons;
    }

    public SandboxRunResponse run(String scenarioId, SandboxRunRequest request) {
        SandboxScenario scenario = catalog.requireRunnable(scenarioId);
        SandboxRunRequest effectiveRequest = request == null
            ? new SandboxRunRequest(null, null, null, null)
            : request;
        String traceId = currentTraceId();
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        String tenantId = scope.tenantId();
        String actor = RequestContext.currentUserId().orElse("sandbox-system");
        Instant startedAt = Instant.now();
        String runId = "sandbox-run-" + UUID.randomUUID();
        SandboxRun ledger = runs.save(preparingRun(
            runId, tenantId, scenario.id(), effectiveRequest.mode(), traceId, actor, startedAt));

        SandboxRuntimeBaseline baseline;
        try {
            baseline = switch (effectiveRequest.mode()) {
                case CURRENT -> baselines.resolveCurrent();
                case HISTORICAL_EXACT ->
                    baselines.resolveHistorical(effectiveRequest.replayCaseId());
                case COMPARE -> baselines.resolveCompare(effectiveRequest.replayCaseId());
            };
            ledger = runs.save(resolvedRun(ledger, baseline, actor));
        } catch (RuntimeException exception) {
            runs.save(failedRun(ledger, exception, actor));
            throw exception;
        }

        try {
            SandboxRunResponse response = (switch (baseline.mode()) {
                case HISTORICAL_EXACT -> executeHistorical(scenario, traceId, baseline);
                case COMPARE -> executeCompare(scenario, traceId, baseline);
                case CURRENT -> execute(scenario, effectiveRequest, traceId, baseline);
            })
                .withRuntime(runId, baseline);
            runs.save(completedRun(ledger, response, actor));
            return response;
        } catch (RuntimeException exception) {
            runs.save(failedRun(ledger, exception, actor));
            throw exception;
        }
    }

    private SandboxRunResponse executeHistorical(
            SandboxScenario scenario,
            String traceId,
            SandboxRuntimeBaseline baseline) {
        List<SandboxStepTrace> steps = new ArrayList<>();
        steps.add(SandboxStepTrace.ok(
            "REPLAY_MANIFEST",
            "/api/v1/engine/sandbox/replay-cases/" + baseline.replayCaseId(),
            json.valueToTree(Map.of("replayCaseId", baseline.replayCaseId())),
            json.valueToTree(Map.of(
                "sourceRuntimeReleaseRef", baseline.runtimeReleaseRef(),
                "sourceRuntimeRevisionNo", baseline.runtimeRevisionNo(),
                "assetCount", baseline.historicalReplay().assets().size(),
                "deidentificationProfile",
                baseline.historicalReplay().replayCase().deidentificationProfile())),
            facts(
                "resolutionSource", SandboxResolutionSource.REPLAY_MANIFEST,
                "manifestHash", baseline.historicalReplay().replayCase().manifestHash(),
                "externalSideEffects", false)));
        try {
            List<SandboxReplayRuleResult> results = replayRules.execute(baseline.historicalReplay());
            long hitCount = results.stream().filter(SandboxReplayRuleResult::hit).count();
            steps.add(SandboxStepTrace.ok(
                "HISTORICAL_RULES",
                "/internal/engine/rules/dsl-evaluator",
                json.valueToTree(Map.of(
                    "replayCaseId", baseline.replayCaseId(),
                    "ruleCount", results.size())),
                json.valueToTree(results),
                facts("ruleCount", results.size(), "hitCount", hitCount, "writeBack", false)));
            return audited(new SandboxRunResponse(
                scenario.id(), traceId, null, null, SandboxRunMode.HISTORICAL_EXACT,
                baseline.runtimeReleaseRef(), baseline.runtimeRevisionNo(),
                SandboxResolutionSource.REPLAY_MANIFEST, false,
                steps, null, null, 0, null, null, null, null, null, null, List.of(),
                "PASS", baseline.replayCaseId(), results, null));
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "HISTORICAL_RULES",
                "/internal/engine/rules/dsl-evaluator",
                json.valueToTree(Map.of("replayCaseId", baseline.replayCaseId())),
                messageOf(exception)));
            return audited(new SandboxRunResponse(
                scenario.id(), traceId, null, null, SandboxRunMode.HISTORICAL_EXACT,
                baseline.runtimeReleaseRef(), baseline.runtimeRevisionNo(),
                SandboxResolutionSource.REPLAY_MANIFEST, false,
                steps, null, null, 0, null, null, null, null, null, null, List.of(),
                "FAIL", baseline.replayCaseId(), List.of(), null));
        }
    }

    private SandboxRunResponse executeCompare(
            SandboxScenario scenario,
            String traceId,
            SandboxRuntimeBaseline baseline) {
        List<SandboxStepTrace> steps = new ArrayList<>();
        steps.add(SandboxStepTrace.ok(
            "REPLAY_MANIFEST",
            "/api/v1/engine/sandbox/replay-cases/" + baseline.replayCaseId(),
            json.valueToTree(Map.of("replayCaseId", baseline.replayCaseId())),
            json.valueToTree(Map.of(
                "historicalRuntimeReleaseRef",
                baseline.historicalReplay().replayCase().sourceRuntimeReleaseRef(),
                "historicalRuntimeRevisionNo",
                baseline.historicalReplay().replayCase().sourceRuntimeRevisionNo(),
                "currentRuntimeReleaseId", baseline.runtimeReleaseId(),
                "currentRuntimeRevisionNo", baseline.runtimeRevisionNo())),
            facts(
                "contextHash", baseline.historicalReplay().replayCase().contextSnapshotHash(),
                "manifestHash", baseline.historicalReplay().replayCase().manifestHash(),
                "externalSideEffects", false)));

        List<SandboxComparableRuleResult> historical;
        try {
            historical = historicalRules.execute(baseline.historicalReplay());
            steps.add(SandboxStepTrace.ok(
                "HISTORICAL_RULES",
                "/internal/engine/rules/dsl-evaluator",
                json.valueToTree(Map.of("replayCaseId", baseline.replayCaseId())),
                json.valueToTree(historical),
                facts("ruleCount", historical.size(), "writeBack", false)));
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "HISTORICAL_RULES", "/internal/engine/rules/dsl-evaluator",
                json.valueToTree(Map.of("replayCaseId", baseline.replayCaseId())),
                messageOf(exception)));
            return audited(compareFailure(scenario, traceId, baseline, steps));
        }

        List<SandboxComparableRuleResult> current;
        try {
            current = currentRules.execute(
                baseline.runtimeContent(), baseline.historicalReplay().contextSnapshot());
            steps.add(SandboxStepTrace.ok(
                "CURRENT_RULES",
                "/internal/engine/rules/dsl-evaluator",
                json.valueToTree(Map.of(
                    "runtimeReleaseId", baseline.runtimeReleaseId(),
                    "runtimeRevisionNo", baseline.runtimeRevisionNo())),
                json.valueToTree(current),
                facts("ruleCount", current.size(), "writeBack", false)));
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "CURRENT_RULES", "/internal/engine/rules/dsl-evaluator",
                json.valueToTree(Map.of("runtimeReleaseId", baseline.runtimeReleaseId())),
                messageOf(exception)));
            return audited(compareFailure(scenario, traceId, baseline, steps));
        }

        try {
            SandboxComparisonResponse comparison = comparisons.compare(
                baseline.historicalReplay().replayCase().contextSnapshotHash(), historical, current);
            steps.add(SandboxStepTrace.ok(
                "COMPARISON",
                "/internal/engine/sandbox/comparison",
                json.valueToTree(Map.of(
                    "historicalRuleCount", historical.size(),
                    "currentRuleCount", current.size())),
                json.valueToTree(comparison),
                facts(
                    "differenceCount", comparison.summary().differenceCount(),
                    "nonComparableCount", comparison.summary().nonComparableCount(),
                    "writeBack", false)));
            return audited(new SandboxRunResponse(
                scenario.id(), traceId, null, null, SandboxRunMode.COMPARE,
                baseline.runtimeReleaseRef(), baseline.runtimeRevisionNo(),
                baseline.resolutionSource(), false,
                steps, null, null, 0, null, null, null, null, null, null, List.of(),
                "PASS", baseline.replayCaseId(), List.of(), comparison));
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "COMPARISON", "/internal/engine/sandbox/comparison",
                json.valueToTree(Map.of("replayCaseId", baseline.replayCaseId())),
                messageOf(exception)));
            return audited(compareFailure(scenario, traceId, baseline, steps));
        }
    }

    private static SandboxRunResponse compareFailure(
            SandboxScenario scenario,
            String traceId,
            SandboxRuntimeBaseline baseline,
            List<SandboxStepTrace> steps) {
        return new SandboxRunResponse(
            scenario.id(), traceId, null, null, SandboxRunMode.COMPARE,
            baseline.runtimeReleaseRef(), baseline.runtimeRevisionNo(),
            baseline.resolutionSource(), false,
            steps, null, null, 0, null, null, null, null, null, null, List.of(),
            "FAIL", baseline.replayCaseId(), List.of(), null);
    }

    private SandboxRunResponse execute(
            SandboxScenario scenario,
            SandboxRunRequest effectiveRequest,
            String traceId,
            SandboxRuntimeBaseline baseline) {
        List<SandboxStepTrace> steps = new ArrayList<>();
        String snapshotId = null;
        String triggerId = null;
        int cardCount = 0;
        PlaybookOutcome playbookOutcome = PlaybookOutcome.ready();
        List<String> embedModes = embedModes(scenario, effectiveRequest);

        ContextSnapshotRequest snapshotRequest;
        try {
            snapshotRequest = SandboxRequestFactory.snapshot(
                scenario, effectiveRequest, traceId, json,
                String.valueOf(baseline.runtimeRevisionNo()));
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "CONTEXT", CONTEXT_ENDPOINT, json.valueToTree(effectiveRequest), messageOf(exception)));
            return audited(fail(
                scenario.id(), traceId, steps, null, null, 0, playbookOutcome, embedModes));
        }

        ContextSnapshotResponse snapshotResponse;
        try {
            snapshotResponse = snapshots.createBound(
                snapshotRequest,
                "sandbox:" + scenario.id() + ":" + traceId,
                baseline.runtimeReleaseId());
            snapshotId = snapshotResponse.snapshotId();
            steps.add(SandboxStepTrace.ok(
                "CONTEXT",
                CONTEXT_ENDPOINT,
                json.valueToTree(snapshotRequest),
                json.valueToTree(snapshotResponse),
                facts(
                    "snapshotId", snapshotResponse.snapshotId(),
                    "status", String.valueOf(snapshotResponse.status()),
                    "qualityStatus", String.valueOf(snapshotResponse.qualityStatus()))));
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "CONTEXT", CONTEXT_ENDPOINT, json.valueToTree(snapshotRequest), messageOf(exception)));
            return audited(fail(
                scenario.id(), traceId, steps, null, null, 0, playbookOutcome, embedModes));
        }

        playbookOutcome = runPlaybook(
            scenario, snapshotId, traceId, steps);
        if (!playbookOutcome.passed()) {
            return audited(fail(
                scenario.id(), traceId, steps, snapshotId, null, 0, playbookOutcome, embedModes));
        }

        RecommendationTriggerRequest recommendationRequest = SandboxRequestFactory.trigger(
            scenario, snapshotId, effectiveRequest, traceId, playbookOutcome.patientPathwayId(),
            String.valueOf(baseline.runtimeRevisionNo()));
        RecommendationEvaluationResponse recommendationResponse;
        try {
            recommendationResponse = recommendations.evaluate(recommendationRequest);
            triggerId = recommendationResponse.triggerId();
            cardCount = recommendationResponse.visibleCardCount();
            steps.add(SandboxStepTrace.ok(
                "RECOMMENDATION",
                RECOMMENDATION_ENDPOINT,
                json.valueToTree(recommendationRequest),
                json.valueToTree(recommendationResponse),
                facts(
                    "triggerId", recommendationResponse.triggerId(),
                    "status", String.valueOf(recommendationResponse.status()),
                    "visibleCardCount", recommendationResponse.visibleCardCount(),
                    "suppressedCardCount", recommendationResponse.suppressedCardCount(),
                    "expectedRuleCode", scenario.expectedRuleCode())));
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "RECOMMENDATION",
                RECOMMENDATION_ENDPOINT,
                json.valueToTree(recommendationRequest),
                messageOf(exception)));
            return audited(fail(
                scenario.id(), traceId, steps, snapshotId, null, 0, playbookOutcome, embedModes));
        }

        EmbedLaunchTokenRequest tokenRequest = SandboxRequestFactory.launchToken(
            scenario, effectiveRequest, traceId);
        try {
            EmbedLaunchTokenResponse tokenResponse = embed.generateToken(tokenRequest);
            steps.add(SandboxStepTrace.ok(
                "TOKEN",
                TOKEN_ENDPOINT,
                json.valueToTree(tokenRequest),
                tokenTraceResponse(tokenResponse),
                facts(
                    "integrationMode", String.valueOf(tokenResponse.integrationMode()),
                    "expiredAt", tokenResponse.expiredAt(),
                    "hook", tokenResponse.hook(),
                    "hookInstance", tokenResponse.hookInstance())));
            return audited(new SandboxRunResponse(
                scenario.id(),
                traceId,
                steps,
                snapshotId,
                triggerId,
                cardCount,
                tokenResponse.token(),
                tokenResponse.embedUrl(),
                tokenResponse.hookInstance(),
                playbookOutcome.patientPathwayId(),
                playbookOutcome.followupPlanId(),
                playbookOutcome.evaluationRunId(),
                embedModes,
                "PASS"));
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "TOKEN", TOKEN_ENDPOINT, json.valueToTree(tokenRequest), messageOf(exception)));
            return audited(fail(
                scenario.id(), traceId, steps, snapshotId, triggerId, cardCount,
                playbookOutcome, embedModes));
        }
    }

    private PlaybookOutcome runPlaybook(
            SandboxScenario scenario,
            String snapshotId,
            String traceId,
            List<SandboxStepTrace> steps) {
        return switch (scenario.playbook()) {
            case "RULE_ONLY", "RECOMMENDATION_COMPOSITE", "EMBED" -> PlaybookOutcome.ready();
            case "PATHWAY" -> runPathway(scenario, snapshotId, steps);
            case "FOLLOWUP" -> runFollowup(scenario, snapshotId, traceId, steps);
            case "EVALUATION" -> runEvaluation(scenario, snapshotId, steps);
            default -> {
                steps.add(SandboxStepTrace.fail(
                    "PLAYBOOK",
                    "/api/v1/engine/sandbox",
                    json.valueToTree(Map.of("playbook", scenario.playbook())),
                    "不支持的沙盘剧本: " + scenario.playbook()));
                yield PlaybookOutcome.failed();
            }
        };
    }

    private PlaybookOutcome runPathway(
            SandboxScenario scenario,
            String snapshotId,
            List<SandboxStepTrace> steps) {
        JsonNode enterRequestNode = json.valueToTree(Map.of(
            "contextSnapshotId", snapshotId,
            "templateCode", scenario.expectedAssetCode()));
        PatientPathwayDetailResponse enterResponse;
        String patientPathwayId;
        try {
            PathwayTemplateFilter filter = new PathwayTemplateFilter(
                PathwayTemplateStatus.PUBLISHED, null, scenario.expectedAssetCode(), null);
            PathwayTemplate template = pathways.listTemplates(filter, PageRequest.defaults())
                .items()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "未找到已发布路径模板 " + scenario.expectedAssetCode()));
            PatientPathwayEnterRequest request = new PatientPathwayEnterRequest(
                snapshotId, "patient-view", template.templateId(), template.startNodeCode());
            enterRequestNode = json.valueToTree(request);
            enterResponse = pathways.enterPatientPathway(request);
            patientPathwayId = enterResponse.patientPathway().patientPathwayId();
            steps.add(SandboxStepTrace.ok(
                "PATHWAY_ENTER",
                PATHWAY_ENTER_ENDPOINT,
                enterRequestNode,
                json.valueToTree(enterResponse),
                facts(
                    "templateCode", template.templateCode(),
                    "templateId", template.templateId(),
                    "patientPathwayId", patientPathwayId,
                    "currentNodeCode", enterResponse.patientPathway().currentNodeCode(),
                    "status", String.valueOf(enterResponse.patientPathway().status()))));
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "PATHWAY_ENTER", PATHWAY_ENTER_ENDPOINT, enterRequestNode, messageOf(exception)));
            return PlaybookOutcome.failed();
        }

        PathwayAdvanceRequest advanceRequest = new PathwayAdvanceRequest(
            patientPathwayId,
            PathwayAdvanceEventType.COMPLETE,
            enterResponse.patientPathway().currentNodeCode(),
            null,
            null,
            null,
            null,
            null,
            pathwayAdvanceEventId(scenario, snapshotId),
            snapshotId);
        try {
            PathwayAdvanceResponse advanceResponse = pathways.advance(advanceRequest);
            steps.add(SandboxStepTrace.ok(
                "PATHWAY_ADVANCE",
                PATHWAY_ADVANCE_ENDPOINT.replace("{patientPathwayId}", patientPathwayId),
                json.valueToTree(advanceRequest),
                json.valueToTree(advanceResponse),
                facts(
                    "patientPathwayId", patientPathwayId,
                    "previousNodeCode", advanceResponse.previousNodeCode(),
                    "nextNodeCode", advanceResponse.nextNodeCode(),
                    "edgeCode", advanceResponse.edgeCode(),
                    "edgeType", advanceResponse.edgeType(),
                    "status", String.valueOf(advanceResponse.status()),
                    "snapshotId", advanceResponse.snapshotId())));
            return new PlaybookOutcome(true, patientPathwayId, null, null);
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "PATHWAY_ADVANCE",
                PATHWAY_ADVANCE_ENDPOINT.replace("{patientPathwayId}", patientPathwayId),
                json.valueToTree(advanceRequest),
                messageOf(exception)));
            return new PlaybookOutcome(false, patientPathwayId, null, null);
        }
    }

    private PlaybookOutcome runFollowup(
            SandboxScenario scenario,
            String snapshotId,
            String traceId,
            List<SandboxStepTrace> steps) {
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            snapshotId,
            "HIGH",
            List.of("QUESTIONNAIRE"),
            "sandbox:" + scenario.id() + ":" + snapshotId,
            Boolean.FALSE,
            null);
        try {
            FollowupPlanDetailResponse response = followups.generatePlan(request);
            steps.add(SandboxStepTrace.ok(
                "FOLLOWUP",
                FOLLOWUP_ENDPOINT,
                json.valueToTree(request),
                json.valueToTree(response),
                facts(
                    "followupPlanId", response.planId(),
                    "status", String.valueOf(response.status()),
                    "taskCount", response.tasks().size(),
                    "modelStatus", String.valueOf(response.modelStatus()),
                    "traceId", traceId)));
            return new PlaybookOutcome(true, null, response.planId(), null);
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "FOLLOWUP", FOLLOWUP_ENDPOINT, json.valueToTree(request), messageOf(exception)));
            return PlaybookOutcome.failed();
        }
    }

    private PlaybookOutcome runEvaluation(
            SandboxScenario scenario,
            String snapshotId,
            List<SandboxStepTrace> steps) {
        EvaluationEvaluateSnapshotRequest request = new EvaluationEvaluateSnapshotRequest(
            snapshotId, scenario.id());
        try {
            EvaluationRunResponse response = evaluations.evaluateSnapshot(request);
            steps.add(SandboxStepTrace.ok(
                "EVALUATION",
                EVALUATION_ENDPOINT,
                json.valueToTree(request),
                json.valueToTree(response),
                facts(
                    "evaluationRunId", response.runId(),
                    "status", String.valueOf(response.status()),
                    "resultCount", response.resultCount(),
                    "findingCount", response.findingCount(),
                    "taskCount", response.taskCount())));
            return new PlaybookOutcome(true, null, null, response.runId());
        } catch (RuntimeException exception) {
            steps.add(SandboxStepTrace.fail(
                "EVALUATION", EVALUATION_ENDPOINT, json.valueToTree(request), messageOf(exception)));
            return PlaybookOutcome.failed();
        }
    }

    private SandboxRun preparingRun(
            String runId,
            String tenantId,
            String scenarioId,
            SandboxRunMode mode,
            String traceId,
            String actor,
            Instant startedAt) {
        return new SandboxRun(
            null, runId, tenantId, scenarioId, mode, null, null, null, null, null, null, null,
            null, null, SandboxExternalSideEffectStatus.SUPPRESSED,
            SandboxRunStatus.PREPARING, null, null, startedAt, null, traceId,
            startedAt, actor, startedAt, actor);
    }

    private SandboxRun resolvedRun(
            SandboxRun ledger,
            SandboxRuntimeBaseline baseline,
            String actor) {
        Map<String, Object> frozenManifest = new LinkedHashMap<>();
        if (baseline.runtimeContent() != null) {
            frozenManifest.put("currentRuntime", baseline.runtimeContent());
        }
        if (baseline.historicalReplay() != null) {
            frozenManifest.put("historicalReplay", baseline.historicalReplay());
        }
        String assetManifestJson = writeJson(frozenManifest);
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("mode", baseline.mode());
        fingerprint.put("replayCaseId", baseline.replayCaseId());
        fingerprint.put(
            "replayManifestHash",
            baseline.historicalReplay() == null
                ? null
                : baseline.historicalReplay().replayCase().manifestHash());
        fingerprint.put("tenantId", baseline.tenantId());
        fingerprint.put("targetOrgUnitId", baseline.targetOrgUnitId());
        fingerprint.put("runtimeReleaseRef", baseline.runtimeReleaseRef());
        fingerprint.put("runtimeRevisionNo", baseline.runtimeRevisionNo());
        fingerprint.put("platformBaselineReleaseId", baseline.platformBaselineReleaseId());
        fingerprint.put("manifestSha256", baseline.manifestSha256());
        fingerprint.put("resolutionSource", baseline.resolutionSource());
        fingerprint.put("assetManifest", frozenManifest);
        String baselineHash = Sha256ContentHash.sha256(
            writeJson(fingerprint), "沙盘运行基线不能为空");
        Instant now = Instant.now();
        return new SandboxRun(
            ledger.id(), ledger.runId(), ledger.tenantId(), ledger.scenarioId(), baseline.mode(),
            baseline.replayCaseId(), baseline.baselineId(), baseline.runtimeReleaseRef(),
            baseline.runtimeRevisionNo(), baseline.platformBaselineReleaseId(),
            baseline.manifestSha256(), baseline.resolutionSource(), assetManifestJson, baselineHash,
            SandboxExternalSideEffectStatus.SUPPRESSED, SandboxRunStatus.RUNNING,
            null, null, ledger.startedAt(), null, ledger.traceId(), ledger.createdAt(),
            ledger.createdBy(), now, actor);
    }

    private static SandboxRun completedRun(
            SandboxRun ledger,
            SandboxRunResponse response,
            String actor) {
        boolean passed = "PASS".equals(response.result());
        String failureMessage = passed ? null : response.steps().stream()
            .filter(step -> "FAIL".equals(step.status()))
            .map(SandboxStepTrace::error)
            .filter(message -> message != null && !message.isBlank())
            .findFirst()
            .orElse("沙盘领域链路执行失败");
        Instant now = Instant.now();
        return copyRun(
            ledger,
            passed ? SandboxRunStatus.PASSED : SandboxRunStatus.FAILED,
            passed ? null : "SANDBOX_EXECUTION_FAILED",
            failureMessage,
            now,
            actor);
    }

    private static SandboxRun failedRun(
            SandboxRun ledger,
            RuntimeException exception,
            String actor) {
        String failureCode = exception instanceof ApiException apiException
            ? apiException.errorCode().code()
            : "SANDBOX_RUNTIME_ERROR";
        return copyRun(
            ledger, SandboxRunStatus.FAILED, failureCode, messageOf(exception), Instant.now(), actor);
    }

    private static SandboxRun copyRun(
            SandboxRun ledger,
            SandboxRunStatus status,
            String failureCode,
            String failureMessage,
            Instant completedAt,
            String actor) {
        return new SandboxRun(
            ledger.id(), ledger.runId(), ledger.tenantId(), ledger.scenarioId(), ledger.mode(),
            ledger.replayCaseId(), ledger.baselineId(), ledger.runtimeReleaseRef(),
            ledger.runtimeRevisionNo(), ledger.platformBaselineReleaseId(),
            ledger.manifestSha256(), ledger.resolutionSource(),
            ledger.assetManifestJson(), ledger.baselineHash(), ledger.externalSideEffectStatus(),
            status, failureCode, truncate(failureMessage, 1024), ledger.startedAt(), completedAt,
            ledger.traceId(), ledger.createdAt(), ledger.createdBy(), completedAt, actor);
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化沙盘运行基线", exception);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private SandboxRunResponse audited(SandboxRunResponse response) {
        audit.record(
            AuditAction.EXECUTE,
            "sandbox_scenario",
            response.scenarioId(),
            "沙盘编排结果=" + response.result()
                + " steps=" + response.steps().size()
                + " cardCount=" + response.cardCount());
        return response;
    }

    private SandboxRunResponse fail(
            String scenarioId,
            String traceId,
            List<SandboxStepTrace> steps,
            String snapshotId,
            String triggerId,
            int cardCount,
            PlaybookOutcome playbookOutcome,
            List<String> embedModes) {
        return new SandboxRunResponse(
            scenarioId,
            traceId,
            steps,
            snapshotId,
            triggerId,
            cardCount,
            null,
            null,
            playbookOutcome.patientPathwayId(),
            playbookOutcome.followupPlanId(),
            playbookOutcome.evaluationRunId(),
            embedModes,
            "FAIL");
    }

    private static List<String> embedModes(
            SandboxScenario scenario,
            SandboxRunRequest request) {
        if ("EMBED".equals(scenario.playbook())) {
            return List.of("IFRAME", "SDK", "API");
        }
        return List.of(request.integrationMode().name());
    }

    private ObjectNode tokenTraceResponse(EmbedLaunchTokenResponse response) {
        ObjectNode node = json.createObjectNode();
        node.put("embedUrl", response.embedUrl());
        node.put("expiredAt", String.valueOf(response.expiredAt()));
        node.put("integrationMode", String.valueOf(response.integrationMode()));
        node.put("launchEndpoint", response.launchEndpoint());
        node.put("hook", response.hook());
        node.put("hookInstance", response.hookInstance());
        return node;
    }

    private static Map<String, Object> facts(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            Object value = entries[index + 1];
            if (value != null) {
                result.put(String.valueOf(entries[index]), value);
            }
        }
        return result;
    }

    private static String currentTraceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null || traceId.isBlank() ? RequestContext.snapshot().traceId() : traceId;
    }

    private static String pathwayAdvanceEventId(SandboxScenario scenario, String snapshotId) {
        String source = snapshotId == null || snapshotId.isBlank() ? "snapshot" : snapshotId;
        String normalized = source.replaceAll("[^A-Za-z0-9]", "");
        String suffix = normalized.length() <= 12
            ? normalized
            : normalized.substring(normalized.length() - 12);
        return "sandbox:" + scenario.id() + ":" + suffix + ":advance";
    }

    private static String messageOf(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
    }

    private record PlaybookOutcome(
        boolean passed,
        String patientPathwayId,
        String followupPlanId,
        String evaluationRunId
    ) {

        private static PlaybookOutcome ready() {
            return new PlaybookOutcome(true, null, null, null);
        }

        private static PlaybookOutcome failed() {
            return new PlaybookOutcome(false, null, null, null);
        }
    }
}
