package com.medkernel.engine.knowledge.production.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.medkernel.engine.authoring.GeneratedAssetCandidateRequest;
import com.medkernel.engine.authoring.GeneratedAssetCandidateService;
import com.medkernel.engine.authoring.GeneratedAssetDraftResponse;
import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
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
import com.medkernel.engine.knowledge.production.NewIdentitySpec;
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
import com.medkernel.engine.llm.ModelEgressConfirmationChallenge;
import com.medkernel.engine.llm.ModelTaskRequest;
import com.medkernel.engine.llm.ModelTaskResponse;
import com.medkernel.engine.llm.provider.DeploymentForm;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
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
    private static final String CAPABILITY = "knowledge.production.knowledge";
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
    private final GeneratedAssetCandidateService generatedAssets = mock(GeneratedAssetCandidateService.class);
    private final KnowledgeIdentityRepository identityRepository = mock(KnowledgeIdentityRepository.class);

    private final ModelKnowledgeProducer producer = new ModelKnowledgeProducer(
        jobRepository,
        readinessService,
        modelGateway,
        production,
        gateService,
        triageService,
        shadowService,
        generatedAssets,
        identityRepository,
        new ObjectMapper());

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot("trace-model", OrgScope.tenant(TENANT), "u-model"));
        when(jobRepository.findByTenantIdAndJobCode(TENANT, JOB_CODE)).thenReturn(Optional.of(job(KnowledgeProducer.API_MODEL)));
        when(readinessService.evaluate(KnowledgeProducer.API_MODEL, CAPABILITY, PROVIDER))
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
            new ReviewRoutingDecision(RoleCode.ENGINE_OPERATOR, KnowledgeDomain.CLINICAL)));
        when(identityRepository.findByTenantIdAndId(TENANT, 101L))
            .thenReturn(Optional.of(identity(101L, "knowledge:htn:model",
                com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE, "高血压知识候选")));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void readinessBlockReturnsStructuredReasonWithoutCallingModel() {
        when(readinessService.evaluate(KnowledgeProducer.API_MODEL, CAPABILITY, PROVIDER))
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
    void rejectsCapabilityThatDoesNotMatchFormalKnowledgeProduction() {
        ModelKnowledgeProductionRequest mismatched = new ModelKnowledgeProductionRequest(
            "knowledge.discovery",
            "请基于来源锚点生成医学知识候选",
            PROVIDER,
            60,
            "knowledge:htn:model",
            "高血压知识候选",
            List.of(sourceRef()),
            SourceAuthorityLevel.B_GUIDELINE,
            KnowledgeRiskLevel.MEDIUM,
            target());

        assertThatThrownBy(() -> producer.generate(JOB_CODE, mismatched))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining(CAPABILITY);

        verify(readinessService, never()).evaluate(any(), any(), any());
        verify(modelGateway, never()).submitTask(any());
    }

    @Test
    void rejectsMissingCapabilityAsBadRequestInsteadOfNullPointer() {
        ModelKnowledgeProductionRequest missing = new ModelKnowledgeProductionRequest(
            null,
            "请基于来源锚点生成医学知识候选",
            PROVIDER,
            60,
            "knowledge:htn:model",
            "高血压知识候选",
            List.of(sourceRef()),
            SourceAuthorityLevel.B_GUIDELINE,
            KnowledgeRiskLevel.MEDIUM,
            target());

        assertThatThrownBy(() -> producer.generate(JOB_CODE, missing))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining(CAPABILITY);

        verify(readinessService, never()).evaluate(any(), any(), any());
        verify(modelGateway, never()).submitTask(any());
    }

    @Test
    void structuralAssetJobMaterializesRuleDraftThroughUnifiedAuthoringRegistry() {
        Instant now = Instant.EPOCH;
        when(jobRepository.findByTenantIdAndJobCode(TENANT, JOB_CODE)).thenReturn(Optional.of(
            new KnowledgeProductionJob(
                1L, TENANT, JOB_CODE, "source-version:9", VersionedAssetType.RULE,
                KnowledgeProducer.API_MODEL, TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL,
                MODEL_STRATEGY, ProductionJobStatus.PENDING, 0, "{}", now, "u", now, "u", "trace-model")));
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(successfulRuleModelTask());
        when(generatedAssets.materializeDraft(any())).thenReturn(new GeneratedAssetDraftResponse(
            "av-rule-1",
            VersionedAssetType.RULE,
            "RULE.CKD.DOSE",
            "V1",
            AssetVersionStatus.DRAFT,
            "a".repeat(64),
            "trace-model"));
        ArgumentCaptor<GeneratedAssetCandidateRequest> generatedCaptor =
            ArgumentCaptor.forClass(GeneratedAssetCandidateRequest.class);

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, ruleRequest());

        assertThat(result.summary().candidates()).singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.assetType()).isEqualTo(VersionedAssetType.RULE);
                assertThat(candidate.candidateRef()).isEqualTo("asset-version:av-rule-1");
            });
        verify(generatedAssets).materializeDraft(generatedCaptor.capture());
        assertThat(generatedCaptor.getValue().assetType()).isEqualTo(VersionedAssetType.RULE);
        assertThat(generatedCaptor.getValue().assetIdentity()).isEqualTo("RULE.CKD.DOSE");
        assertThat(generatedCaptor.getValue().content().path("ruleCode").asText()).isEqualTo("RULE.CKD.DOSE");
        assertThat(generatedCaptor.getValue().content().path("fieldCatalogIdentity").asText())
            .isEqualTo("FIELD.CATALOG.CLINICAL_CONTEXT");
        assertThat(generatedCaptor.getValue().content().path("generationEvidence").path("modelTaskId").asText())
            .isEqualTo("task-rule-1");
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
        assertThat(envelope.assetType()).isEqualTo(VersionedAssetType.KNOWLEDGE);
        assertThat(envelope.assetIdentity()).isEqualTo("knowledge:htn:model");
        assertThat(envelope.subject()).isEqualTo("高血压知识候选");
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
    void newIdentityGenerationCarriesAuthoritativeOutputContextToGateway() {
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(successfulModelTask());
        ArgumentCaptor<ModelTaskRequest> taskCaptor = ArgumentCaptor.forClass(ModelTaskRequest.class);

        producer.generate(JOB_CODE, requestWithNewIdentity());

        verify(modelGateway).submitTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().authoritativeOutputContext())
            .contains(
                "\"domain\":\"DIAGNOSTIC_ITEM\"",
                "\"subject\":\"检验项目说明书来源与使用边界\"",
                "\"sourceRef\":\"GL-HTN-2024:v1:section-1\"",
                "\"clinicalActionable\":false");
    }

    @Test
    void existingIdentityGenerationCarriesIdentityDomainInAuthoritativeOutputContext() {
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(successfulModelTask());
        ArgumentCaptor<ModelTaskRequest> taskCaptor = ArgumentCaptor.forClass(ModelTaskRequest.class);

        producer.generate(JOB_CODE, request());

        verify(modelGateway).submitTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().authoritativeOutputContext())
            .contains(
                "\"domain\":\"GUIDELINE\"",
                "\"subject\":\"高血压知识候选\"",
                "\"sourceRef\":\"GL-HTN-2024:v1:section-1\"",
                "\"clinicalActionable\":false");
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
    void localModelJobForcesLocalRouteAndProviderIntoGateway() {
        String localProvider = "ollama-hospital";
        when(jobRepository.findByTenantIdAndJobCode(TENANT, JOB_CODE))
            .thenReturn(Optional.of(job(KnowledgeProducer.LOCAL_MODEL)));
        when(readinessService.evaluate(KnowledgeProducer.LOCAL_MODEL, CAPABILITY, localProvider))
            .thenReturn(localReady(localProvider));
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(successfulLocalModelTask());
        ArgumentCaptor<ModelTaskRequest> taskCaptor = ArgumentCaptor.forClass(ModelTaskRequest.class);

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, request(localProvider));

        assertThat(result.modelMode()).isEqualTo("B1");
        assertThat(result.summary().candidates()).singleElement()
            .satisfies(candidate -> assertThat(candidate.candidateRef()).isEqualTo("candidate:model:1"));
        verify(modelGateway).submitTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().requiredRouteStrategy()).isEqualTo("LOCAL_MODEL");
        assertThat(taskCaptor.getValue().providerCode()).isEqualTo(localProvider);
        verify(production).submitCandidate(eq(JOB_CODE), any(KnowledgeAssetEnvelope.class), eq(target()));
    }

    @Test
    void apiModelJobUsesLocalRouteWhenReadinessConfirmsHospitalRuntimeLocalProvider() {
        String localProvider = "ollama-launch";
        when(readinessService.evaluate(KnowledgeProducer.API_MODEL, CAPABILITY, localProvider))
            .thenReturn(apiModelHospitalRuntimeReady(localProvider));
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(successfulLocalModelTask());
        ArgumentCaptor<ModelTaskRequest> taskCaptor = ArgumentCaptor.forClass(ModelTaskRequest.class);

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, request(localProvider));

        assertThat(result.modelMode()).isEqualTo("B1");
        assertThat(result.summary().candidates()).singleElement()
            .satisfies(candidate -> assertThat(candidate.candidateRef()).isEqualTo("candidate:model:1"));
        verify(modelGateway).submitTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().requiredRouteStrategy()).isEqualTo("LOCAL_MODEL");
        assertThat(taskCaptor.getValue().providerCode()).isEqualTo(localProvider);
        verify(production).submitCandidate(eq(JOB_CODE), any(KnowledgeAssetEnvelope.class), eq(target()));
    }

    @Test
    void localModelJobRejectsPlatformSourcePipelineBeforeModelInvocation() {
        when(jobRepository.findByTenantIdAndJobCode(TENANT, JOB_CODE))
            .thenReturn(Optional.of(job(KnowledgeProducer.LOCAL_MODEL, TargetPipeline.PLATFORM_SOURCE)));

        assertThatThrownBy(() -> producer.generate(JOB_CODE, request("ollama-hospital")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("本地模型")
            .hasMessageContaining("院内覆盖");

        verify(readinessService, never()).evaluate(any(), any(), any());
        verify(modelGateway, never()).submitTask(any());
        verify(production, never()).submitCandidate(any(), any(), any());
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
                    assertThat(gate.reason()).contains("候选要求");
                }));
        verify(gateService, never()).evaluate(any(), any());
        verify(production, never()).submitCandidate(any(), any(), any());
    }

    @Test
    void b0FallbackSkipsWithoutCreatingCandidate() {
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(new ModelTaskResponse(
            "task-b0", "DEGRADED", "{\"status\":\"B0_BASELINE\"}", "B0", "B0-Deterministic-Baseline",
            "baseline", "gateway-default", "[]", null, null, true,
            "EGRESS_BLOCKED：模型使用边界缺失，已降级 B0", 25L, "trace-model"));

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, request());

        assertThat(result.summary().candidates()).isEmpty();
        assertThat(result.summary().skipped()).singleElement()
            .satisfies(skipped -> assertThat(skipped.reason()).contains("B0", "模型使用边界"));
        verify(gateService, never()).evaluate(any(), any());
        verify(production, never()).submitCandidate(any(), any(), any());
    }

    @Test
    void egressConfirmationRequiredBlocksWithActionableGateBeforeCandidateChain() {
        when(modelGateway.submitTask(any(ModelTaskRequest.class))).thenReturn(new ModelTaskResponse(
            "task-confirmation", "CONFIRMATION_REQUIRED",
            "{\"status\":\"CONFIRMATION_REQUIRED\",\"payloadHash\":\"sha256-confirmation-required\"}",
            "B2", "claude-opus-4", "prompt:aikstd13-v1", "tool:submit-candidate-v1",
            "[]", null, "HIGH", false, null, 18L, "trace-model",
            new ModelEgressConfirmationChallenge(
                CAPABILITY,
                "sha256-confirmation-required",
                List.of("prompt"),
                PROVIDER,
                "高敏外调需要责任确认")));

        ModelKnowledgeProductionResult result = producer.generate(JOB_CODE, request());

        assertThat(result.egressConfirmation()).isNotNull();
        assertThat(result.egressConfirmation().payloadHash()).isEqualTo("sha256-confirmation-required");
        assertThat(result.summary().candidates()).isEmpty();
        assertThat(result.summary().skipped()).isEmpty();
        assertThat(result.summary().blocked()).singleElement()
            .satisfies(blocked -> assertThat(blocked.failedGates()).singleElement()
                .satisfies(gate -> {
                    assertThat(gate.code()).isEqualTo(ModelKnowledgeProducer.MODEL_EGRESS_CONFIRMATION_GATE);
                    assertThat(gate.reason())
                        .contains("责任确认", "sha256-confirmation-required", "prompt", PROVIDER)
                        .doesNotContain("降级 B0");
                }));
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
        return request(PROVIDER);
    }

    private ModelKnowledgeProductionRequest request(String providerCode) {
        return new ModelKnowledgeProductionRequest(
            CAPABILITY,
            "请基于来源锚点生成一条高血压 AI 候选规则",
            providerCode,
            60,
            "knowledge:htn:model",
            "高血压知识候选",
            List.of(sourceRef()),
            SourceAuthorityLevel.B_GUIDELINE,
            KnowledgeRiskLevel.MEDIUM,
            target());
    }

    private ModelKnowledgeProductionRequest requestWithNewIdentity() {
        return new ModelKnowledgeProductionRequest(
            CAPABILITY,
            "请基于来源锚点生成检验项目说明书来源边界",
            PROVIDER,
            60,
            "launch.diagnostic-item.source-boundary",
            "检验项目说明书来源与使用边界",
            List.of(sourceRef()),
            SourceAuthorityLevel.B_GUIDELINE,
            KnowledgeRiskLevel.LOW,
            new MaterializationTarget(null, new NewIdentitySpec(
                com.medkernel.engine.knowledge.KnowledgeDomain.DIAGNOSTIC_ITEM,
                "检验项目说明书来源与使用边界",
                "launch.diagnostic-item.source-boundary")));
    }

    private ModelKnowledgeProductionRequest ruleRequest() {
        return new ModelKnowledgeProductionRequest(
            CAPABILITY,
            "请基于来源锚点生成一条慢性肾病用药剂量复核规则",
            PROVIDER,
            60,
            "RULE.CKD.DOSE",
            "慢性肾病用药剂量复核规则",
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

    private KnowledgeIdentity identity(Long id, String identityCode,
                                       com.medkernel.engine.knowledge.KnowledgeDomain domain,
                                       String subject) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new KnowledgeIdentity(
            id,
            TENANT,
            identityCode,
            domain,
            subject,
            null,
            null,
            KnowledgeIdentityStatus.ACTIVE,
            null,
            now,
            "tester",
            now,
            "tester");
    }

    private KnowledgeProductionJob job(KnowledgeProducer jobProducer) {
        return job(jobProducer, TargetPipeline.TENANT_OVERLAY);
    }

    private KnowledgeProductionJob job(KnowledgeProducer jobProducer, TargetPipeline targetPipeline) {
        Instant now = Instant.EPOCH;
        return new KnowledgeProductionJob(
            1L, TENANT, JOB_CODE, "source-version:9", VersionedAssetType.KNOWLEDGE,
            jobProducer, targetPipeline, KnowledgeDomain.CLINICAL, MODEL_STRATEGY,
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
                KnowledgeProductionReadinessItem.pass("EGRESS_GOVERNANCE", "公网模型使用边界已配置", CAPABILITY),
                KnowledgeProductionReadinessItem.pass("VERSION_TRIPLE", "三元组已声明", MODEL_STRATEGY)));
    }

    private KnowledgeProductionReadinessResponse localReady(String providerCode) {
        return new KnowledgeProductionReadinessResponse(
            TENANT,
            KnowledgeProducer.LOCAL_MODEL,
            CAPABILITY,
            providerCode,
            DeploymentForm.HOSPITAL_RUNTIME,
            false,
            false,
            List.of(
                KnowledgeProductionReadinessItem.pass("LITERATURE_ROOT", "已配置", "s3://mk/lit"),
                KnowledgeProductionReadinessItem.pass("DEPLOYMENT_FORM", "本地模型生产器允许运行", "HOSPITAL_RUNTIME"),
                KnowledgeProductionReadinessItem.pass("MODEL_PROVIDER", "本地 provider 已健康", providerCode),
                KnowledgeProductionReadinessItem.pass("MODEL_EVALUATION", "评测通过", "runId=1"),
                KnowledgeProductionReadinessItem.pass("EGRESS_GOVERNANCE", "院内本地模型使用边界已配置", providerCode),
                KnowledgeProductionReadinessItem.pass("MODEL_POLICY", "策略匹配", "LOCAL_MODEL"),
                KnowledgeProductionReadinessItem.pass("VERSION_TRIPLE", "三元组已声明", MODEL_STRATEGY)));
    }

    private KnowledgeProductionReadinessResponse apiModelHospitalRuntimeReady(String providerCode) {
        return new KnowledgeProductionReadinessResponse(
            TENANT,
            KnowledgeProducer.API_MODEL,
            CAPABILITY,
            providerCode,
            DeploymentForm.HOSPITAL_RUNTIME,
            false,
            false,
            List.of(
                KnowledgeProductionReadinessItem.pass("LITERATURE_ROOT", "已配置", "s3://mk/lit"),
                KnowledgeProductionReadinessItem.pass("DEPLOYMENT_FORM", "院内运行态仅允许本地模型调用", "HOSPITAL_RUNTIME"),
                KnowledgeProductionReadinessItem.pass("MODEL_PROVIDER", "本地 provider 已健康", providerCode),
                KnowledgeProductionReadinessItem.pass("MODEL_EVALUATION", "评测通过", "runId=1"),
                KnowledgeProductionReadinessItem.pass("EGRESS_GOVERNANCE", "院内本地模型使用边界已配置", providerCode),
                KnowledgeProductionReadinessItem.pass("MODEL_POLICY", "策略匹配", "LOCAL_MODEL"),
                KnowledgeProductionReadinessItem.pass("VERSION_TRIPLE", "三元组已声明", MODEL_STRATEGY)));
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

    private ModelTaskResponse successfulRuleModelTask() {
        return new ModelTaskResponse(
            "task-rule-1",
            "SUCCEEDED",
            """
                {
                  "schemaVersion": "1.0",
                  "ruleCode": "RULE.CKD.DOSE",
                  "name": "肾功能下降用药剂量复核",
                  "fieldCatalogIdentity": "FIELD.CATALOG.CLINICAL_CONTEXT",
                  "fieldBindings": ["observations[].valueNumeric", "medications[].code"],
                  "terminologyRefs": ["TERM.LOINC", "TERM.ATC"],
                  "triggerBindings": [{"triggerPoint": "ORDER_SIGN", "purpose": "RULE_EXECUTION"}],
                  "dsl": {
                    "when": {"all": [{"field": "observations[].valueNumeric", "operator": "<", "value": 30}]},
                    "then": [{"actionCardRef": "ACTION.CKD.DOSE_REVIEW"}],
                    "explain": {"message": "肾功能下降时需复核剂量。"}
                  }
                }
                """,
            "B2",
            "claude-opus-4",
            "prompt:aikstd13-v1",
            "tool:submit-candidate-v1",
            "[{\"sourceRef\":\"GL-HTN-2024:v1:section-1\"}]",
            0.88,
            "MEDIUM",
            false,
            null,
            140L,
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

    private ModelTaskResponse successfulLocalModelTask() {
        return new ModelTaskResponse(
            "task-model-local-b1",
            "SUCCEEDED",
            "{\"sections\":{\"summary\":\"本地模型生成院内覆盖候选规则\"}}",
            "B1",
            "qwen2.5:7b",
            "prompt:aikstd13-v1",
            "tool:submit-candidate-v1",
            "[{\"sourceRef\":\"GL-HTN-2024:v1:section-1\"}]",
            0.82,
            "MEDIUM",
            false,
            null,
            80L,
            "trace-model");
    }
}
