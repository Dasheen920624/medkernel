package com.medkernel.engine.knowledge.production.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.authoring.GeneratedAssetCandidateRequest;
import com.medkernel.engine.authoring.GeneratedAssetCandidateService;
import com.medkernel.engine.authoring.GeneratedAssetDraftResponse;
import com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.production.CandidateSubmissionResponse;
import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.KnowledgeProducer;
import com.medkernel.engine.knowledge.production.KnowledgeProductionJob;
import com.medkernel.engine.knowledge.production.KnowledgeProductionOrchestrationService;
import com.medkernel.engine.knowledge.production.MaterializationTarget;
import com.medkernel.engine.knowledge.production.NewIdentitySpec;
import com.medkernel.engine.knowledge.production.ProductionJobRequest;
import com.medkernel.engine.knowledge.production.ProductionJobResponse;
import com.medkernel.engine.knowledge.production.ProductionJobStatus;
import com.medkernel.engine.knowledge.production.ReviewRoutingDecision;
import com.medkernel.engine.knowledge.production.TargetPipeline;
import com.medkernel.engine.knowledge.production.gate.AikGateResultRepository;
import com.medkernel.engine.knowledge.production.gate.AnchorCompleteGate;
import com.medkernel.engine.knowledge.production.gate.ApplicableScopeGate;
import com.medkernel.engine.knowledge.production.gate.AuthorityLevelGate;
import com.medkernel.engine.knowledge.production.gate.CandidateSafetyGateService;
import com.medkernel.engine.knowledge.production.gate.ContentFormatGate;
import com.medkernel.engine.knowledge.production.gate.GateItemResult;
import com.medkernel.engine.knowledge.production.gate.GateOutcome;
import com.medkernel.engine.knowledge.production.gate.ReviewElementsGate;
import com.medkernel.engine.knowledge.production.gate.SourcePresentGate;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowDecision;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowEvaluationService;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowRunStatus;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageAction;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageContext;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageDecision;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageState;
import com.medkernel.engine.knowledge.production.triage.KnowledgeGenerationTriageService;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** AIK-STD-04 候选生成编排服务单元测试。 */
class CandidateGenerationOrchestrationServiceTest {

    private final SourceVersionRepository versions = mock(SourceVersionRepository.class);
    private final SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
    private final SourceFragmentRepository fragments = mock(SourceFragmentRepository.class);
    private final KnowledgeIdentityRepository identities = mock(KnowledgeIdentityRepository.class);
    private final KnowledgeProductionOrchestrationService production =
        mock(KnowledgeProductionOrchestrationService.class);
    private final SourceCandidateGenerator generator =
        new SourceCandidateGenerator(new ProfessionalAssetTemplateRegistry(), new ObjectMapper());
    private final AikGateResultRepository gateResults = mock(AikGateResultRepository.class);
    private final CandidateSafetyGateService gateService = new CandidateSafetyGateService(
        List.of(new SourcePresentGate(), new AnchorCompleteGate(), new AuthorityLevelGate(),
            new ContentFormatGate(), new ReviewElementsGate(), new ApplicableScopeGate()),
        gateResults);
    private final KnowledgeGenerationTriageService triageService = mock(KnowledgeGenerationTriageService.class);
    private final KnowledgeShadowEvaluationService shadowService = mock(KnowledgeShadowEvaluationService.class);
    private final GeneratedAssetCandidateService generatedAssets = mock(GeneratedAssetCandidateService.class);

    private final CandidateGenerationOrchestrationService service =
        new CandidateGenerationOrchestrationService(
            versions, documents, fragments, identities, generator, production, gateService, triageService,
            shadowService, generatedAssets);

    @BeforeEach
    void bindTenant() {
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("t-1"), "u-1"));
        when(triageService.evaluate(any(), any())).thenReturn(new GenerationTriageDecision(
            1L, GenerationTriageState.MINOR_REVISION, GenerationTriageAction.MERGE_REVIEW,
            null, null, "进入审核"));
        when(shadowService.evaluate(any(), any())).thenReturn(new KnowledgeShadowDecision(
            1L, KnowledgeShadowRunStatus.PASSED, true, "影子评测通过"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    private void seedVersionAndDocument() {
        when(versions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(
            new SourceVersion(9L, "t-1", 7L, "v1", Instant.EPOCH, "a".repeat(64), "file://gl", "zh",
                Instant.EPOCH, "sys")));
        when(documents.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(
            new SourceDocument(7L, "t-1", "GL-2024", SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE,
                "卫健委指南", "高血压指南", "卫健委", "CC-BY", "zh", Instant.EPOCH, "sys", Instant.EPOCH, "sys")));
    }

    private GenerationItem item(VersionedAssetType type) {
        return new GenerationItem(type, new MaterializationTarget(null, new NewIdentitySpec(
            com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE, "高血压规则", "RULE-HTN-1")));
    }

    @Test
    void materializesStructuralAssetsAsUnifiedDraftsInsteadOfKnowledgeCandidates() {
        seedVersionAndDocument();
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L)).thenReturn(List.of(
            new SourceFragment(1L, "t-1", 9L, "section-1", "总则", "血压≥140/90。", "b".repeat(64),
                Instant.EPOCH)));
        when(production.createJob(any(ProductionJobRequest.class))).thenAnswer(invocation ->
            ProductionJobResponse.from(new KnowledgeProductionJob(
                1L, "t-1", "job-" + invocation.<ProductionJobRequest>getArgument(0).assetType(), "s",
                invocation.<ProductionJobRequest>getArgument(0).assetType(),
                KnowledgeProducer.MANUAL, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL, null,
                ProductionJobStatus.PENDING, 0, "{}", Instant.EPOCH, "sys", Instant.EPOCH, "sys", "trace")));
        when(generatedAssets.materializeDraft(any())).thenAnswer(invocation -> {
            GeneratedAssetCandidateRequest draft = invocation.getArgument(0);
            return new GeneratedAssetDraftResponse(
                "av-" + draft.assetType(), draft.assetType(), draft.assetIdentity(), "V1",
                AssetVersionStatus.DRAFT, "d".repeat(64), "trace-1");
        });

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(
                new GenerationItem(VersionedAssetType.RULE, new MaterializationTarget(null, new NewIdentitySpec(
                    com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE, "高血压规则", "RULE-HTN-1"))),
                new GenerationItem(VersionedAssetType.PATHWAY, new MaterializationTarget(null, new NewIdentitySpec(
                    com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE, "高血压路径", "PATHWAY-HTN-1"))))));

        assertThat(summary.candidates()).hasSize(2);
        assertThat(summary.candidates())
            .extracting(GeneratedCandidate::candidateRef)
            .containsExactly("asset-version:av-RULE", "asset-version:av-PATHWAY");
        ArgumentCaptor<GeneratedAssetCandidateRequest> draftCaptor =
            ArgumentCaptor.forClass(GeneratedAssetCandidateRequest.class);
        verify(generatedAssets, times(2)).materializeDraft(draftCaptor.capture());
        assertThat(draftCaptor.getAllValues())
            .extracting(GeneratedAssetCandidateRequest::assetType)
            .containsExactly(VersionedAssetType.RULE, VersionedAssetType.PATHWAY);
        GeneratedAssetCandidateRequest ruleDraft = draftCaptor.getAllValues().get(0);
        assertThat(ruleDraft.assetIdentity()).isEqualTo("RULE-HTN-1");
        assertThat(ruleDraft.sourceRef()).isEqualTo("source-version:9");
        assertThat(ruleDraft.createdBy()).isEqualTo("u-1");
        assertThat(ruleDraft.content().path("ruleCode").asText()).isEqualTo("RULE-HTN-1");
        assertThat(ruleDraft.content().has("packageVersion")).isFalse();
        assertThat(ruleDraft.content().has("versionNo")).isFalse();
        GeneratedAssetCandidateRequest pathwayDraft = draftCaptor.getAllValues().get(1);
        assertThat(pathwayDraft.assetIdentity()).isEqualTo("PATHWAY-HTN-1");
        assertThat(pathwayDraft.content().path("pathwayCode").asText()).isEqualTo("PATHWAY-HTN-1");
        verify(production, never()).submitCandidate(any(), any(), any());
        verify(production, times(2)).completeJob(any());
        verify(production, never()).cancelJob(any());
    }

    @Test
    void skipsAllWhenSourceHasNoFragments() {
        seedVersionAndDocument();
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L))
            .thenReturn(List.of());

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(item(VersionedAssetType.KNOWLEDGE))));

        assertThat(summary.candidates()).isEmpty();
        assertThat(summary.skipped()).hasSize(1);
        assertThat(summary.skipped().get(0).reason()).contains("无源");
        verify(production, never()).createJob(any());
    }

    @Test
    void candidateFailingGateIsBlockedNotSubmitted() {
        seedVersionAndDocument();
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L)).thenReturn(List.of(
            new SourceFragment(1L, "t-1", 9L, "section-1", "总则", "血压≥140/90。", "b".repeat(64),
                Instant.EPOCH)));
        when(production.createJob(any(ProductionJobRequest.class))).thenAnswer(invocation ->
            ProductionJobResponse.from(new KnowledgeProductionJob(
                1L, "t-1", "job-x", "s", invocation.<ProductionJobRequest>getArgument(0).assetType(),
                KnowledgeProducer.MANUAL, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL, null,
                ProductionJobStatus.PENDING, 0, "{}", Instant.EPOCH, "sys", Instant.EPOCH, "sys", "trace")));
        CandidateSafetyGateService blockingGate = mock(CandidateSafetyGateService.class);
        when(blockingGate.evaluate(any(), any())).thenReturn(new GateOutcome(false,
            List.of(GateItemResult.fail("SOURCE_PRESENT", "无来源（无源资产拒收）"))));
        CandidateGenerationOrchestrationService blockingService =
            new CandidateGenerationOrchestrationService(
                versions, documents, fragments, identities, generator, production, blockingGate, triageService,
                shadowService, generatedAssets);

        GenerationSummary summary = blockingService.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(item(VersionedAssetType.KNOWLEDGE))));

        assertThat(summary.candidates()).isEmpty();
        assertThat(summary.blocked()).hasSize(1);
        assertThat(summary.blocked().get(0).failedGates()).isNotEmpty();
        verify(production, never()).submitCandidate(any(), any(), any());
        verify(production).cancelJob("job-x");
        verify(production, never()).completeJob(any());
    }

    @Test
    void shadowNotReadyIsBlockedNotSubmitted() {
        seedVersionAndDocument();
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L)).thenReturn(List.of(
            new SourceFragment(1L, "t-1", 9L, "section-1", "总则", "血压≥140/90。", "b".repeat(64),
                Instant.EPOCH)));
        when(production.createJob(any(ProductionJobRequest.class))).thenAnswer(invocation ->
            ProductionJobResponse.from(new KnowledgeProductionJob(
                1L, "t-1", "job-x", "s", invocation.<ProductionJobRequest>getArgument(0).assetType(),
                KnowledgeProducer.MANUAL, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL, null,
                ProductionJobStatus.PENDING, 0, "{}", Instant.EPOCH, "sys", Instant.EPOCH, "sys", "trace")));
        when(shadowService.evaluate(any(), any())).thenReturn(new KnowledgeShadowDecision(
            9L, KnowledgeShadowRunStatus.NOT_READY, false, "未配置真实影子评测基准集"));

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(item(VersionedAssetType.KNOWLEDGE))));

        assertThat(summary.candidates()).isEmpty();
        assertThat(summary.blocked()).singleElement()
            .satisfies(blocked -> assertThat(blocked.failedGates()).singleElement()
                .satisfies(gate -> {
                    assertThat(gate.code()).isEqualTo("SHADOW_EVAL");
                    assertThat(gate.reason()).contains("基准集");
                }));
        verify(production, never()).submitCandidate(any(), any(), any());
        verify(production).cancelJob("job-x");
        verify(production, never()).completeJob(any());
    }

    @Test
    void duplicateTriageIsSkippedAndNotSubmitted() {
        seedVersionAndDocument();
        when(identities.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(new KnowledgeIdentity(
            10L, "t-1", "RULE-EXISTING-10", com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE,
            "既有规则", null, null, KnowledgeIdentityStatus.ACTIVE, null,
            Instant.EPOCH, "sys", Instant.EPOCH, "sys")));
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L)).thenReturn(List.of(
            new SourceFragment(1L, "t-1", 9L, "section-1", "总则", "血压≥140/90。", "b".repeat(64),
                Instant.EPOCH)));
        when(production.createJob(any(ProductionJobRequest.class))).thenAnswer(invocation ->
            ProductionJobResponse.from(new KnowledgeProductionJob(
                1L, "t-1", "job-x", "s", invocation.<ProductionJobRequest>getArgument(0).assetType(),
                KnowledgeProducer.MANUAL, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL, null,
                ProductionJobStatus.PENDING, 0, "{}", Instant.EPOCH, "sys", Instant.EPOCH, "sys", "trace")));
        when(triageService.evaluate(any(), any())).thenReturn(new GenerationTriageDecision(
            9L, GenerationTriageState.DUPLICATE, GenerationTriageAction.SKIP_DUPLICATE,
            5L, 5L, "content_hash 与既有版本一致，重复候选跳过"));

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(new GenerationItem(VersionedAssetType.KNOWLEDGE, new MaterializationTarget(10L, null)))));

        assertThat(summary.candidates()).isEmpty();
        assertThat(summary.skipped()).singleElement()
            .satisfies(skipped -> assertThat(skipped.reason()).contains("重复"));
        verify(production, never()).submitCandidate(any(), any(), any());
        verify(production).completeJob("job-x");
        verify(production, never()).cancelJob(any());
        verify(triageService).evaluate(any(), org.mockito.ArgumentMatchers.argThat(
            (GenerationTriageContext context) -> context.targetIdentityId().equals(10L)));
    }

    @Test
    void loadsExistingIdentityDomainForKnowledgeTemplateSelection() {
        seedVersionAndDocument();
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L)).thenReturn(List.of(
            new SourceFragment(1L, "t-1", 9L, "section-1", "说明书", "来源片段", "b".repeat(64),
                Instant.EPOCH)));
        when(identities.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(new KnowledgeIdentity(
            10L, "t-1", "DRUG-1", com.medkernel.engine.knowledge.KnowledgeDomain.DRUG,
            "药品说明书", null, null, KnowledgeIdentityStatus.ACTIVE, null,
            Instant.EPOCH, "sys", Instant.EPOCH, "sys")));
        when(production.createJob(any(ProductionJobRequest.class))).thenAnswer(invocation ->
            ProductionJobResponse.from(new KnowledgeProductionJob(
                1L, "t-1", "job-x", "s", invocation.<ProductionJobRequest>getArgument(0).assetType(),
                KnowledgeProducer.MANUAL, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL, null,
                ProductionJobStatus.PENDING, 0, "{}", Instant.EPOCH, "sys", Instant.EPOCH, "sys", "trace")));
        when(production.submitCandidate(eq("job-x"), any(), any())).thenReturn(
            new CandidateSubmissionResponse("kv:10:draft-from-v1",
                new ReviewRoutingDecision(RoleCode.ENGINE_OPERATOR, KnowledgeDomain.CLINICAL)));

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(new GenerationItem(
                VersionedAssetType.KNOWLEDGE, new MaterializationTarget(10L, null)))));

        assertThat(summary.candidates()).hasSize(1);
        verify(identities).findByTenantIdAndId("t-1", 10L);
        verify(production).submitCandidate(eq("job-x"), org.mockito.ArgumentMatchers.argThat(
            envelope -> envelope.payload().contains("\"template\":\"DRUG\"")
                && envelope.assetIdentity().equals("DRUG-1")), any());
    }

    @Test
    void usesNewIdentityDeclaredDomainForKnowledgeTemplateSelection() {
        seedVersionAndDocument();
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L)).thenReturn(List.of(
            new SourceFragment(1L, "t-1", 9L, "section-1", "护理", "来源片段", "b".repeat(64),
                Instant.EPOCH)));
        when(production.createJob(any(ProductionJobRequest.class))).thenAnswer(invocation ->
            ProductionJobResponse.from(new KnowledgeProductionJob(
                1L, "t-1", "job-x", "s", invocation.<ProductionJobRequest>getArgument(0).assetType(),
                KnowledgeProducer.MANUAL, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL, null,
                ProductionJobStatus.PENDING, 0, "{}", Instant.EPOCH, "sys", Instant.EPOCH, "sys", "trace")));
        when(production.submitCandidate(eq("job-x"), any(), any())).thenReturn(
            new CandidateSubmissionResponse("kv:11:draft-from-v1",
                new ReviewRoutingDecision(RoleCode.ENGINE_OPERATOR, KnowledgeDomain.CLINICAL)));
        MaterializationTarget target = new MaterializationTarget(null, new NewIdentitySpec(
            com.medkernel.engine.knowledge.KnowledgeDomain.NURSING, "护理知识", "NURSING-1"));

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(new GenerationItem(VersionedAssetType.KNOWLEDGE, target))));

        assertThat(summary.candidates()).hasSize(1);
        verify(identities, never()).findByTenantIdAndId(any(), any());
        verify(production).submitCandidate(eq("job-x"), org.mockito.ArgumentMatchers.argThat(
            envelope -> envelope.payload().contains("\"template\":\"NURSING\"")), eq(target));
    }
}
