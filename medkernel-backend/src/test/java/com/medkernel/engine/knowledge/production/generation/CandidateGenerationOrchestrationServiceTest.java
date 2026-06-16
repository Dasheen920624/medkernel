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
import com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
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
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** AIK-STD-04 候选生成编排服务单元测试。 */
class CandidateGenerationOrchestrationServiceTest {

    private final SourceVersionRepository versions = mock(SourceVersionRepository.class);
    private final SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
    private final SourceFragmentRepository fragments = mock(SourceFragmentRepository.class);
    private final KnowledgeProductionOrchestrationService production =
        mock(KnowledgeProductionOrchestrationService.class);
    private final SourceCandidateGenerator generator =
        new SourceCandidateGenerator(new ProfessionalAssetTemplateRegistry(), new ObjectMapper());
    private final AikGateResultRepository gateResults = mock(AikGateResultRepository.class);
    private final CandidateSafetyGateService gateService = new CandidateSafetyGateService(
        List.of(new SourcePresentGate(), new AnchorCompleteGate(), new AuthorityLevelGate(),
            new ContentFormatGate(), new ReviewElementsGate(), new ApplicableScopeGate()),
        gateResults);

    private final CandidateGenerationOrchestrationService service =
        new CandidateGenerationOrchestrationService(
            versions, documents, fragments, generator, production, gateService);

    @BeforeEach
    void bindTenant() {
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("t-1"), "u-1"));
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
    void generatesCandidatePerTypeViaSubmitCandidate() {
        seedVersionAndDocument();
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L)).thenReturn(List.of(
            new SourceFragment(1L, "t-1", 9L, "section-1", "总则", "血压≥140/90。", "b".repeat(64),
                Instant.EPOCH)));
        when(production.createJob(any(ProductionJobRequest.class))).thenAnswer(invocation ->
            ProductionJobResponse.from(new KnowledgeProductionJob(
                1L, "t-1", "job-x", "s", invocation.<ProductionJobRequest>getArgument(0).assetType(),
                KnowledgeProducer.MANUAL, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL, null,
                ProductionJobStatus.PENDING, 0, "{}", Instant.EPOCH, "sys", Instant.EPOCH, "sys", "trace")));
        when(production.submitCandidate(eq("job-x"), any(), any())).thenReturn(
            new CandidateSubmissionResponse("kv:1:draft-from-v1",
                new ReviewRoutingDecision(RoleCode.KNOWLEDGE_GOVERNOR, RoleCode.KNOWLEDGE_GOVERNOR, false,
                    KnowledgeDomain.CLINICAL)));

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(item(VersionedAssetType.RULE), item(VersionedAssetType.PATHWAY))));

        assertThat(summary.candidates()).hasSize(2);
        assertThat(summary.candidates().get(0).candidateRef()).isEqualTo("kv:1:draft-from-v1");
        assertThat(summary.candidates().get(0).jobCode()).isEqualTo("job-x");
        assertThat(summary.skipped()).isEmpty();
        verify(production, times(2)).createJob(any());
        verify(production, times(2)).submitCandidate(eq("job-x"), any(), any());
    }

    @Test
    void skipsAllWhenSourceHasNoFragments() {
        seedVersionAndDocument();
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L))
            .thenReturn(List.of());

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(item(VersionedAssetType.RULE))));

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
                versions, documents, fragments, generator, production, blockingGate);

        GenerationSummary summary = blockingService.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(item(VersionedAssetType.RULE))));

        assertThat(summary.candidates()).isEmpty();
        assertThat(summary.blocked()).hasSize(1);
        assertThat(summary.blocked().get(0).failedGates()).isNotEmpty();
        verify(production, never()).submitCandidate(any(), any(), any());
    }
}
