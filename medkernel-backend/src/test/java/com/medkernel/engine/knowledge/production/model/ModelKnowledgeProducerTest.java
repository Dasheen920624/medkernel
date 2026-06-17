package com.medkernel.engine.knowledge.production.model;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.production.CandidateSubmissionResponse;
import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.KnowledgeProducer;
import com.medkernel.engine.knowledge.production.KnowledgeProductionJob;
import com.medkernel.engine.knowledge.production.KnowledgeProductionJobRepository;
import com.medkernel.engine.knowledge.production.KnowledgeProductionOrchestrationService;
import com.medkernel.engine.knowledge.production.KnowledgeProductionReadinessItem;
import com.medkernel.engine.knowledge.production.KnowledgeProductionReadinessResponse;
import com.medkernel.engine.knowledge.production.KnowledgeProductionReadinessService;
import com.medkernel.engine.knowledge.production.MaterializationTarget;
import com.medkernel.engine.knowledge.production.ProductionJobStatus;
import com.medkernel.engine.knowledge.production.ReviewRoutingDecision;
import com.medkernel.engine.knowledge.production.TargetPipeline;
import com.medkernel.engine.knowledge.production.gate.CandidateSafetyGateService;
import com.medkernel.engine.knowledge.production.gate.GateItemResult;
import com.medkernel.engine.knowledge.production.gate.GateOutcome;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowDecision;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowEvaluationService;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowRunStatus;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageAction;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageDecision;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageState;
import com.medkernel.engine.knowledge.production.triage.KnowledgeGenerationTriageService;
import com.medkernel.engine.llm.ModelGatewayService;
import com.medkernel.engine.llm.ModelTaskRequest;
import com.medkernel.engine.llm.ModelTaskResponse;
import com.medkernel.engine.llm.provider.DeploymentForm;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * AIK-STD-13 FR2 模型生产器测试。
 *
 * <p>模型生产器只能经 readiness 与模型网关产出候选，且必须复用同一门禁、分流、影子评测和提交链。
 */
class ModelKnowledgeProducerTest {

    private static final String TENANT = "tenant-model";
    private static final String JOB_CODE = "job-model";
    private static final String CAPABILITY = "rule.draft";
    private static final String PROVIDER = "claude-prod";
    private static final String MODEL_STRATEGY =
        "prompt:aikstd13-v1;tool:submit-candidate-v1;model:claude-opus-4";

    private final KnowledgeProductionJobRepository jobRepository = mock(KnowledgeProductionJobRepository.class);
    private final KnowledgeProductionReadinessService readinessService = mock(KnowledgeProductionReadinessService.class);
    private final ModelGatewayService modelGateway = mock(ModelGatewayService.class);
    private final KnowledgeProductionOrchestrationService production = mock(KnowledgeProductionOrchestrationService.class);
    private final CandidateSafetyGateService gateService = mock(CandidateSafetyGateService.class);
    private final KnowledgeGenerationTriageService triageService = mock(KnowledgeGenerationTriageService.class);
    private final KnowledgeShadowEvaluationService shadowService = mock(KnowledgeShadowEvaluationService.class);

    private final ModelKnowledgeProducer producer = new ModelKnowledgeProducer(
        jobRepository,
        readinessService,
        modelGateway,
        production,
        gateService,
        triageService,
        shadowService,
        new ObjectMapper());

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot("trace-model", OrgScope.tenant(TENANT), "u-model"));
        when(jobRepository.findByTenantIdAndJobCode(TENANT, JOB_CODE)).thenReturn(Optional.of(job(KnowledgeProducer.API_MODEL)));
        when(readinessService.evaluate(KnowledgeProducer.API_MODEL, CAPABILITY, PROVIDER, MODEL_STRATEGY))
            .thenReturn(ready());
        when(gateService.evaluate(any(), any())).thenReturn(new GateOutcome(true, List.of(
            GateItemResult.pass("SOURCE_PRESENT"))));
        when(triageService.evaluate(any(), any())).thenReturn(new GenerationTriageDecision(
            1L, GenerationTriageState.MINOR_REVISION, GenerationTriageAction.MERGE_REVIEW,
            null, null, "进入审核"));
        when(shadowService.evaluate(any(), any())).thenReturn(new KnowledgeShadowDecision(
            1L, KnowledgeShadowRunStatus.PASSED, true, "影子评测通过"));
        when(production.submitCandidate(eq(JOB_CODE), any(), any())).thenReturn(new CandidateSubmissionResponse(
            "candidate:model:1",
            new ReviewRoutingDecision(RoleCode.KNOWLEDGE_GOVERNOR, RoleCode.CLINICAL_GOVERNOR,
                false, KnowledgeDomain.CLINICAL)));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void readinessBlockReturnsStructuredReasonWithoutCallingModel() {
        when(readinessService.evaluate(KnowledgeProducer.API_MODEL, CAPABILITY, PROVIDER, MODEL_STRATEGY))
            .thenReturn(blocked("LITERATURE_ROOT", "平台知识文献资料库根地址未配置"));

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, request());

        assertThat(result.summary().candidates()).isEmpty();
        assertThat(result.summary().blocked()).singleElement()
            .satisfies(blocked -> assertThat(blocked.failedGates()).singleElement()
                .satisfies(gate -> {
                    assertThat(gate.code()).isEqualTo("LITERATURE_ROOT");
                    assertThat(gate.reason()).contains("文献资料库");
                }));
        verify(modelGateway, never()).submitTask(any());
        verify(production, never()).submitCandidate(any(), any(), any());
    }

    @Test
    void providerSuccessSubmitsAiCandidateThroughSharedProductionChain() {
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(successfulModelTask());
        ArgumentCaptor<KnowledgeAssetEnvelope> envelopeCaptor = ArgumentCaptor.forClass(KnowledgeAssetEnvelope.class);
        ArgumentCaptor<ModelTaskRequest> taskCaptor = ArgumentCaptor.forClass(ModelTaskRequest.class);

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, request());

        assertThat(result.modelTaskId()).isEqualTo("task-model-1");
        assertThat(result.modelMode()).isEqualTo("B2");
        assertThat(result.modelVersion()).isEqualTo("claude-opus-4");
        assertThat(result.promptVersion()).isEqualTo("prompt:aikstd13-v1");
        assertThat(result.toolVersion()).isEqualTo("tool:submit-candidate-v1");
        assertThat(result.summary().candidates()).singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.jobCode()).isEqualTo(JOB_CODE);
                assertThat(candidate.candidateRef()).isEqualTo("candidate:model:1");
            });

        verify(modelGateway).submitTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().capabilityCode()).isEqualTo(CAPABILITY);
        assertThat(taskCaptor.getValue().inputData()).contains("请基于来源锚点生成");

        verify(gateService).evaluate(envelopeCaptor.capture(), any());
        verify(production).submitCandidate(eq(JOB_CODE), any(KnowledgeAssetEnvelope.class), eq(target()));
        KnowledgeAssetEnvelope envelope = envelopeCaptor.getValue();
        assertThat(envelope.assetType()).isEqualTo(VersionedAssetType.RULE);
        assertThat(envelope.assetIdentity()).isEqualTo("rule:htn:model");
        assertThat(envelope.subject()).isEqualTo("高血压 AI 候选规则");
        assertThat(envelope.sources()).containsExactly(sourceRef());
        assertThat(envelope.trustLevel()).isEqualTo(SourceAuthorityLevel.B_GUIDELINE);
        assertThat(envelope.riskLevel()).isEqualTo(KnowledgeRiskLevel.MEDIUM);
        assertThat(envelope.orgScope()).isEqualTo(TENANT);
        assertThat(envelope.lifecycleStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(envelope.payload()).contains("\"aiGenerated\":true", "\"modelTaskId\":\"task-model-1\"",
            "\"modelVersion\":\"claude-opus-4\"", "\"promptInputHash\"", "\"sections\"");
        assertThat(envelope.payload()).doesNotContain("请基于来源锚点生成");
        assertThat(envelope.contentHash()).isEqualTo(
            Sha256ContentHash.sha256(envelope.payload(), "资产内容不能为空"));
    }

    @Test
    void localFallbackSuccessStillSubmitsCandidateWithFallbackEvidence() {
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(successfulLocalFallbackModelTask());
        ArgumentCaptor<KnowledgeAssetEnvelope> envelopeCaptor = ArgumentCaptor.forClass(KnowledgeAssetEnvelope.class);

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, request());

        assertThat(result.modelMode()).isEqualTo("B1");
        assertThat(result.summary().candidates()).singleElement()
            .satisfies(candidate -> assertThat(candidate.candidateRef()).isEqualTo("candidate:model:1"));
        verify(production).submitCandidate(eq(JOB_CODE), envelopeCaptor.capture(), eq(target()));
        KnowledgeAssetEnvelope envelope = envelopeCaptor.getValue();
        assertThat(envelope.payload()).contains(
            "\"modelMode\":\"B1\"",
            "\"fallbackUsed\":true",
            "\"fallbackReason\":\"B2 -> B1：外部 provider 限流，本地模型成功\"");
    }

    @Test
    void invalidModelOutputSchemaIsBlockedBeforeGateAndSubmit() {
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(new ModelTaskResponse(
            "task-bad-schema", "SUCCEEDED", "非结构化纯文本", "B2", "claude-opus-4",
            "prompt:aikstd13-v1", "tool:submit-candidate-v1", "[]", 0.72, "MEDIUM",
            false, null, 70L, "trace-model"));

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, request());

        assertThat(result.summary().candidates()).isEmpty();
        assertThat(result.summary().blocked()).singleElement()
            .satisfies(blocked -> assertThat(blocked.failedGates()).singleElement()
                .satisfies(gate -> {
                    assertThat(gate.code()).isEqualTo("MODEL_OUTPUT_SCHEMA");
                    assertThat(gate.reason()).contains("JSON");
                }));
        verify(gateService, never()).evaluate(any(), any());
        verify(production, never()).submitCandidate(any(), any(), any());
    }

    @Test
    void b0FallbackSkipsWithoutCreatingCandidate() {
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(new ModelTaskResponse(
            "task-b0", "DEGRADED", "{\"status\":\"B0_BASELINE\"}", "B0", "B0-Deterministic-Baseline",
            "baseline", "gateway-default", "[]", null, null, true,
            "EGRESS_BLOCKED：出域白名单缺失，已降级 B0", 25L, "trace-model"));

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, request());

        assertThat(result.summary().candidates()).isEmpty();
        assertThat(result.summary().skipped()).singleElement()
            .satisfies(skipped -> assertThat(skipped.reason()).contains("B0", "出域"));
        verify(gateService, never()).evaluate(any(), any());
        verify(production, never()).submitCandidate(any(), any(), any());
    }

    @Test
    void failedProviderTaskSkipsWithHonestStatusReason() {
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(new ModelTaskResponse(
            "task-provider-timeout", "FAILED", "{\"error\":\"timeout\"}", "B2", "claude-opus-4",
            "prompt:aikstd13-v1", "tool:submit-candidate-v1", "[]", null, null, false,
            "PROVIDER_TIMEOUT：外部模型超时", 2_000L, "trace-model"));

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, request());

        assertThat(result.summary().candidates()).isEmpty();
        assertThat(result.summary().skipped()).singleElement()
            .satisfies(skipped -> assertThat(skipped.reason())
                .contains("模型网关未成功", "FAILED", "B2", "PROVIDER_TIMEOUT")
                .doesNotContain("降级 B0"));
        verify(gateService, never()).evaluate(any(), any());
        verify(production, never()).submitCandidate(any(), any(), any());
    }

    private ModelKnowledgeProductionRequest request() {
        return new ModelKnowledgeProductionRequest(
            CAPABILITY,
            "请基于来源锚点生成一条高血压 AI 候选规则",
            PROVIDER,
            60,
            "rule:htn:model",
            "高血压 AI 候选规则",
            List.of(sourceRef()),
            SourceAuthorityLevel.B_GUIDELINE,
            KnowledgeRiskLevel.MEDIUM,
            target());
    }

    private MaterializationTarget target() {
        return new MaterializationTarget(101L, null);
    }

    private AssetSourceRef sourceRef() {
        return new AssetSourceRef("GL-HTN-2024:v1:section-1", SourceAuthorityLevel.B_GUIDELINE);
    }

    private KnowledgeProductionJob job(KnowledgeProducer jobProducer) {
        Instant now = Instant.EPOCH;
        return new KnowledgeProductionJob(
            1L, TENANT, JOB_CODE, "source-version:9", VersionedAssetType.RULE,
            jobProducer, TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL, MODEL_STRATEGY,
            ProductionJobStatus.PENDING, 0, "{}", now, "u", now, "u", "trace-model");
    }

    private KnowledgeProductionReadinessResponse ready() {
        return new KnowledgeProductionReadinessResponse(
            TENANT,
            KnowledgeProducer.API_MODEL,
            CAPABILITY,
            PROVIDER,
            DeploymentForm.PRODUCTION_CENTER,
            false,
            false,
            List.of(
                KnowledgeProductionReadinessItem.pass("LITERATURE_ROOT", "已配置", "s3://mk/lit"),
                KnowledgeProductionReadinessItem.pass("MODEL_PROVIDER", "provider 已健康", PROVIDER),
                KnowledgeProductionReadinessItem.pass("MODEL_EVALUATION", "评测通过", "runId=1"),
                KnowledgeProductionReadinessItem.pass("EGRESS_GOVERNANCE", "白名单已配置", CAPABILITY),
                KnowledgeProductionReadinessItem.pass("VERSION_TRIPLE", "三元组已声明", MODEL_STRATEGY),
                KnowledgeProductionReadinessItem.pass("P6_ACCEPTANCE", "P6 已验收", "true")));
    }

    private KnowledgeProductionReadinessResponse blocked(String code, String message) {
        return new KnowledgeProductionReadinessResponse(
            TENANT,
            KnowledgeProducer.API_MODEL,
            CAPABILITY,
            PROVIDER,
            DeploymentForm.PRODUCTION_CENTER,
            false,
            false,
            List.of(KnowledgeProductionReadinessItem.block(code, message, "<missing>")));
    }

    private ModelTaskResponse successfulModelTask() {
        return new ModelTaskResponse(
            "task-model-1",
            "SUCCEEDED",
            "{\"sections\":{\"summary\":\"基于受控来源生成候选规则，仅作为待审草稿\"}}",
            "B2",
            "claude-opus-4",
            "prompt:aikstd13-v1",
            "tool:submit-candidate-v1",
            "[{\"sourceRef\":\"GL-HTN-2024:v1:section-1\"}]",
            0.91,
            "MEDIUM",
            false,
            null,
            120L,
            "trace-model");
    }

    private ModelTaskResponse successfulLocalFallbackModelTask() {
        return new ModelTaskResponse(
            "task-model-fallback-b1",
            "SUCCEEDED",
            "{\"sections\":{\"summary\":\"本地模型在外部限流后生成候选规则\"}}",
            "B1",
            "qwen2.5:7b",
            "prompt:aikstd13-v1",
            "tool:submit-candidate-v1",
            "[{\"sourceRef\":\"GL-HTN-2024:v1:section-1\"}]",
            0.81,
            "MEDIUM",
            true,
            "B2 -> B1：外部 provider 限流，本地模型成功",
            95L,
            "trace-model");
    }
}
