package com.medkernel.engine.knowledge.production.initialization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.medkernel.engine.knowledge.CandidateClassification;
import com.medkernel.engine.knowledge.CandidateClassificationRepository;
import com.medkernel.engine.knowledge.CandidateClassificationType;
import com.medkernel.engine.knowledge.CandidateReviewStatus;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeCandidateResponse;
import com.medkernel.engine.knowledge.KnowledgeCandidateReviewRequest;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionService;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.knowledge.production.KnowledgeProducer;
import com.medkernel.engine.knowledge.production.KnowledgeProductionCandidate;
import com.medkernel.engine.knowledge.production.KnowledgeProductionCandidateRepository;
import com.medkernel.engine.knowledge.production.KnowledgeProductionJob;
import com.medkernel.engine.knowledge.production.KnowledgeProductionJobRepository;
import com.medkernel.engine.knowledge.production.ProductionJobStatus;
import com.medkernel.engine.knowledge.production.TargetPipeline;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/** 初始化批次编排测试。 */
class KnowledgeInitializationServiceTest {

    private final SourceVersionRepository sourceVersions = mock(SourceVersionRepository.class);
    private final SourceVersionApprovalRepository sourceApprovals = mock(SourceVersionApprovalRepository.class);
    private final SourceFragmentRepository sourceFragments = mock(SourceFragmentRepository.class);
    private final KnowledgeAssetVersionRepository versions = mock(KnowledgeAssetVersionRepository.class);
    private final CandidateClassificationRepository classifications = mock(CandidateClassificationRepository.class);
    private final KnowledgeProductionCandidateRepository productionCandidates =
        mock(KnowledgeProductionCandidateRepository.class);
    private final KnowledgeProductionJobRepository productionJobs = mock(KnowledgeProductionJobRepository.class);
    private final KnowledgeInitializationBatchRepository batches = mock(KnowledgeInitializationBatchRepository.class);
    private final KnowledgeInitializationItemRepository items = mock(KnowledgeInitializationItemRepository.class);
    private final KnowledgeVersionService versionService = mock(KnowledgeVersionService.class);
    private final KnowledgeInitializationCatalog catalog = new KnowledgeInitializationCatalog();
    private final KnowledgeInitializationManifestValidator validator =
        new KnowledgeInitializationManifestValidator(catalog);
    private final KnowledgeInitializationService service = new KnowledgeInitializationService(
        sourceVersions, sourceApprovals, sourceFragments, versions, classifications,
        productionCandidates, productionJobs, batches, items, versionService, catalog, validator,
        new ObjectMapper());

    @BeforeEach
    void bindContext() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "reviewer"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "reviewer", "n/a", List.of(new SimpleGrantedAuthority(RoleCode.KNOWLEDGE_GOVERNOR.authority()))));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void previewRejectsCandidateWhoseSourceVersionIsNotIndependentlyApproved() {
        seedCandidate();
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(request()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("来源版本未独立批准");
    }

    @Test
    void previewRejectsSourceHashDriftAfterApproval() {
        seedCandidate();
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L)).thenReturn(Optional.of(
            new SourceVersionApproval(
                1L, "t-1", 9L, "f".repeat(64), SourceVersionApprovalStatus.APPROVED,
                "reviewer-a", Instant.EPOCH, "批准", Instant.EPOCH, "reviewer-a")));

        assertThatThrownBy(() -> service.preview(request()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("来源摘要漂移");
    }

    @Test
    void createPersistsServerResolvedManifestAndHashes() {
        seedCandidate();
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L)).thenReturn(Optional.of(
            new SourceVersionApproval(
                1L, "t-1", 9L, "a".repeat(64), SourceVersionApprovalStatus.APPROVED,
                "reviewer-a", Instant.EPOCH, "批准", Instant.EPOCH, "reviewer-a")));
        when(batches.findByTenantIdAndIdempotencyKey("t-1", "init-foundation-f1-1.0.0"))
            .thenReturn(Optional.empty());
        when(batches.save(any())).thenAnswer(invocation -> {
            KnowledgeInitializationBatch batch = invocation.getArgument(0);
            return new KnowledgeInitializationBatch(
                10L, batch.tenantId(), batch.batchCode(), batch.releaseType(), batch.releaseVersion(),
                batch.foundationReleaseVersion(), batch.phase(), batch.status(), batch.sourceManifestHash(),
                batch.candidateManifestHash(), batch.overallHash(), batch.sourceCount(), batch.candidateCount(),
                batch.lowCount(), batch.mediumCount(), batch.highCount(), batch.coverageJson(),
                batch.templateVersion(), batch.modelVersion(), batch.summary(), batch.idempotencyKey(),
                batch.lastBulkIdempotencyKey(), batch.lastBulkAt(), batch.createdAt(), batch.createdBy(),
                batch.updatedAt(), batch.updatedBy());
        });
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeInitializationBatchPreview preview = service.preview(request());
        KnowledgeInitializationBatchView created = service.create(new KnowledgeInitializationBatchCreateRequest(
            request(),
            preview.hashes().sourceManifestHash(),
            preview.hashes().candidateManifestHash(),
            preview.hashes().overallHash()));

        assertThat(created.batch().overallHash()).isEqualTo(preview.hashes().overallHash());
        assertThat(created.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.candidateClassificationId()).isEqualTo(88L);
                assertThat(item.generatedByModelFlag()).isEqualTo("N");
                assertThat(item.riskLevel()).isEqualTo(KnowledgeRiskLevel.MEDIUM);
            });
        verify(batches).save(any(KnowledgeInitializationBatch.class));
        verify(items).save(any(KnowledgeInitializationItem.class));
    }

    @Test
    void createRejectsReusedIdempotencyKeyWithDifferentManifestHash() {
        KnowledgeInitializationBatch existing = batch(KnowledgeRiskLevel.MEDIUM);
        when(batches.findByTenantIdAndIdempotencyKey("t-1", "init-foundation-f1-1.0.0"))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(new KnowledgeInitializationBatchCreateRequest(
            request(),
            "c".repeat(64),
            "d".repeat(64),
            "f".repeat(64))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("幂等键已绑定不同初始化清单");
    }

    @Test
    void lowBulkApprovalRejectsBatchContainingNoPendingLowCandidates() {
        KnowledgeInitializationBatch batch = batch(KnowledgeRiskLevel.MEDIUM);
        when(batches.findByTenantIdAndBatchCode("t-1", "foundation-f1-1.0.0"))
            .thenReturn(Optional.of(batch));
        when(items.findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc("t-1", 10L))
            .thenReturn(List.of(batchItem(KnowledgeRiskLevel.MEDIUM)));

        assertThatThrownBy(() -> service.approveLow(
            "foundation-f1-1.0.0",
            new KnowledgeInitializationBatchApproveRequest(
                batch.overallHash(), "bulk-low-1", "批准低风险候选")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("仅 LOW");
    }

    @Test
    void lowBulkApprovalRejectsBatchOutsideReviewState() {
        KnowledgeInitializationBatch inReview = batch(KnowledgeRiskLevel.LOW);
        KnowledgeInitializationBatch blocked = new KnowledgeInitializationBatch(
            inReview.id(), inReview.tenantId(), inReview.batchCode(), inReview.releaseType(),
            inReview.releaseVersion(), inReview.foundationReleaseVersion(), inReview.phase(),
            KnowledgeInitializationBatchStatus.BLOCKED, inReview.sourceManifestHash(),
            inReview.candidateManifestHash(), inReview.overallHash(), inReview.sourceCount(),
            inReview.candidateCount(), inReview.lowCount(), inReview.mediumCount(),
            inReview.highCount(), inReview.coverageJson(), inReview.templateVersion(),
            inReview.modelVersion(), inReview.summary(), inReview.idempotencyKey(),
            inReview.lastBulkIdempotencyKey(), inReview.lastBulkAt(), inReview.createdAt(),
            inReview.createdBy(), inReview.updatedAt(), inReview.updatedBy());
        when(batches.findByTenantIdAndBatchCode("t-1", "foundation-f1-1.0.0"))
            .thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> service.approveLow(
            "foundation-f1-1.0.0",
            new KnowledgeInitializationBatchApproveRequest(
                blocked.overallHash(), "bulk-low-blocked", "批准低风险候选")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("当前状态不允许 LOW 批审");
    }

    @Test
    void lowBulkApprovalRetryReturnsCompletedBatchForTheSameIdempotencyKey() {
        KnowledgeInitializationBatch inReview = batch(KnowledgeRiskLevel.LOW);
        KnowledgeInitializationBatch completed = new KnowledgeInitializationBatch(
            inReview.id(), inReview.tenantId(), inReview.batchCode(), inReview.releaseType(),
            inReview.releaseVersion(), inReview.foundationReleaseVersion(), inReview.phase(),
            KnowledgeInitializationBatchStatus.COMPLETE, inReview.sourceManifestHash(),
            inReview.candidateManifestHash(), inReview.overallHash(), inReview.sourceCount(),
            inReview.candidateCount(), inReview.lowCount(), inReview.mediumCount(),
            inReview.highCount(), inReview.coverageJson(), inReview.templateVersion(),
            inReview.modelVersion(), inReview.summary(), inReview.idempotencyKey(),
            "bulk-low-completed", Instant.EPOCH, inReview.createdAt(), inReview.createdBy(),
            inReview.updatedAt(), inReview.updatedBy());
        when(batches.findByTenantIdAndBatchCode("t-1", "foundation-f1-1.0.0"))
            .thenReturn(Optional.of(completed));
        when(items.findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc("t-1", 10L))
            .thenReturn(List.of(withItemStatus(
                batchItem(KnowledgeRiskLevel.LOW),
                KnowledgeInitializationItemStatus.APPROVED)));

        KnowledgeInitializationBatchView result = service.approveLow(
            "foundation-f1-1.0.0",
            new KnowledgeInitializationBatchApproveRequest(
                completed.overallHash(), " bulk-low-completed ", "重试低风险批审"));

        assertThat(result.batch().status()).isEqualTo(KnowledgeInitializationBatchStatus.COMPLETE);
    }

    @Test
    void lowBulkApprovalUsesOnlyAuthenticatedRoleCodesAndCompletesFoundationBatch() {
        KnowledgeInitializationBatch batch = batch(KnowledgeRiskLevel.LOW);
        KnowledgeInitializationItem item = batchItem(KnowledgeRiskLevel.LOW);
        when(batches.findByTenantIdAndBatchCode("t-1", "foundation-f1-1.0.0"))
            .thenReturn(Optional.of(batch));
        when(items.findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc("t-1", 10L))
            .thenReturn(List.of(item));
        when(sourceVersions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(sourceVersion("a")));
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(versionService.reviewCandidate(any(), any())).thenReturn(new KnowledgeCandidateResponse(
            1L, List.of(), List.of(), true, "APPROVED", "候选已批准"));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeInitializationBatchView approved = service.approveLow(
            "foundation-f1-1.0.0",
            new KnowledgeInitializationBatchApproveRequest(
                batch.overallHash(), "bulk-low-1", "批准低风险候选"));

        ArgumentCaptor<KnowledgeCandidateReviewRequest> reviewRequest =
            ArgumentCaptor.forClass(KnowledgeCandidateReviewRequest.class);
        verify(versionService).reviewCandidate(any(), reviewRequest.capture());
        assertThat(reviewRequest.getValue().roleCodes())
            .containsExactly(RoleCode.KNOWLEDGE_GOVERNOR.code());
        assertThat(approved.batch().status()).isEqualTo(KnowledgeInitializationBatchStatus.COMPLETE);
        verify(items).save(any(KnowledgeInitializationItem.class));
    }

    @Test
    void lowBulkApprovalCompletesAnApprovedPartialFoundationPhase() {
        KnowledgeInitializationBatch f1 = withBatchPhase(
            batch(KnowledgeRiskLevel.LOW),
            InitializationPhase.F1);
        KnowledgeInitializationItem item = batchItem(KnowledgeRiskLevel.LOW);
        when(batches.findByTenantIdAndBatchCode("t-1", "foundation-f1-1.0.0"))
            .thenReturn(Optional.of(f1));
        when(items.findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc("t-1", 10L))
            .thenReturn(List.of(item));
        when(sourceVersions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(sourceVersion("a")));
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(versionService.reviewCandidate(any(), any())).thenReturn(new KnowledgeCandidateResponse(
            1L, List.of(), List.of(), true, "APPROVED", "候选已批准"));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeInitializationBatchView approved = service.approveLow(
            "foundation-f1-1.0.0",
            new KnowledgeInitializationBatchApproveRequest(
                f1.overallHash(), "bulk-low-f1", "批准基础 F1 低风险候选"));

        assertThat(approved.batch().status()).isEqualTo(KnowledgeInitializationBatchStatus.COMPLETE);
    }

    @Test
    void lowBulkApprovalDoesNotHideAnAlreadyBlockedLowItem() {
        KnowledgeInitializationBatch batch = batch(KnowledgeRiskLevel.LOW);
        KnowledgeInitializationItem pending = batchItem(KnowledgeRiskLevel.LOW);
        KnowledgeInitializationItem blocked = withItemStatus(
            new KnowledgeInitializationItem(
                21L, pending.tenantId(), pending.batchId(), 2, pending.catalogCode(),
                pending.assetType(), "DATA_ELEMENT.BLOCKED", pending.namespace(),
                pending.assetVersion(), pending.sourceVersionId(), pending.sourceHash(),
                "kv:2:1.0.0", 89L, "c".repeat(64), pending.riskLevel(),
                pending.generatedByModelFlag(), pending.dependenciesJson(), pending.governanceJson(),
                pending.changeType(), pending.replacementCanonicalId(), pending.effectiveTo(),
                pending.status(), pending.createdAt(), pending.createdBy(),
                pending.updatedAt(), pending.updatedBy()),
            KnowledgeInitializationItemStatus.BLOCKED);
        when(batches.findByTenantIdAndBatchCode("t-1", "foundation-f1-1.0.0"))
            .thenReturn(Optional.of(batch));
        when(items.findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc("t-1", 10L))
            .thenReturn(List.of(pending, blocked));
        when(sourceVersions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(sourceVersion("a")));
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(versionService.reviewCandidate(any(), any())).thenReturn(new KnowledgeCandidateResponse(
            1L, List.of(), List.of(), true, "APPROVED", "候选已批准"));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeInitializationBatchView approved = service.approveLow(
            "foundation-f1-1.0.0",
            new KnowledgeInitializationBatchApproveRequest(
                batch.overallHash(), "bulk-low-2", "批准低风险候选"));

        assertThat(approved.batch().status()).isEqualTo(KnowledgeInitializationBatchStatus.BLOCKED);
    }

    @Test
    void previewRejectsCandidateWhoseDeclaredAnchorDoesNotExistInApprovedSource() {
        seedCandidate();
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(sourceFragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.preview(request()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("来源锚点不存在");
    }

    @Test
    void previewRejectsCanonicalIdThatDiffersFromProductionLineageIdentity() {
        seedCandidate();
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(productionCandidates.findByTenantIdAndCandidateRefIn(
            "t-1", List.of("kv:1:1.0.0"))).thenReturn(List.of(
                new KnowledgeProductionCandidate(
                    3L, "t-1", "job-1", "DATA_ELEMENT.OTHER", "b".repeat(64),
                    "kv:1:1.0.0", KnowledgeRiskLevel.MEDIUM, Instant.EPOCH, "steward")));

        assertThatThrownBy(() -> service.preview(request()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("canonical ID 与生产血缘身份不一致");
    }

    @Test
    void previewRejectsRiskLevelDriftFromProductionLineage() {
        seedCandidate();
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(productionCandidates.findByTenantIdAndCandidateRefIn(
            "t-1", List.of("kv:1:1.0.0"))).thenReturn(List.of(
                new KnowledgeProductionCandidate(
                    3L, "t-1", "job-1", "DATA_ELEMENT.BP", "b".repeat(64),
                    "kv:1:1.0.0", KnowledgeRiskLevel.LOW, Instant.EPOCH, "steward")));

        assertThatThrownBy(() -> service.preview(request()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("风险等级与生产血缘不一致");
    }

    @Test
    void previewRejectsNewChangeTypeWhenCanonicalAlreadyCompleted() {
        seedCandidate();
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(items.findCompletedHistory("t-1", "DATA_ELEMENT.BP"))
            .thenReturn(List.of(completedItem("urn:medkernel:data-element", "1.0.0")));

        assertThatThrownBy(() -> service.preview(request()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已有完成版本不得声明 NEW");
    }

    @Test
    void previewRejectsNamespaceDriftAcrossCompletedCanonicalVersions() {
        seedCandidate();
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(items.findCompletedHistory("t-1", "DATA_ELEMENT.BP"))
            .thenReturn(List.of(completedItem("urn:medkernel:legacy", "1.0.0")));

        assertThatThrownBy(() -> service.preview(
            request("urn:medkernel:data-element", "1.0.1", InitializationChangeType.PATCH_COMPATIBLE)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("命名空间禁止漂移");
    }

    @Test
    void previewRejectsSemanticVersionThatDoesNotMatchDeclaredChangeType() {
        seedCandidate();
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(items.findCompletedHistory("t-1", "DATA_ELEMENT.BP"))
            .thenReturn(List.of(completedItem("urn:medkernel:data-element", "1.0.0")));

        assertThatThrownBy(() -> service.preview(
            request("urn:medkernel:data-element", "1.1.0", InitializationChangeType.PATCH_COMPATIBLE)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("PATCH_COMPATIBLE 必须只递增 patch");
    }

    @Test
    void refreshCompletesFullReleaseOnlyAfterExistingReviewChainApprovedEveryCandidate() {
        KnowledgeInitializationBatch batch = batch(KnowledgeRiskLevel.HIGH);
        KnowledgeInitializationItem item = batchItem(KnowledgeRiskLevel.HIGH);
        when(batches.findByTenantIdAndBatchCode("t-1", "foundation-f1-1.0.0"))
            .thenReturn(Optional.of(batch));
        when(items.findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc("t-1", 10L))
            .thenReturn(List.of(item));
        when(sourceVersions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(sourceVersion("a")));
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(classifications.findByTenantIdAndId("t-1", 88L))
            .thenReturn(Optional.of(classification(CandidateReviewStatus.APPROVED)));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeInitializationBatchView refreshed = service.refresh("foundation-f1-1.0.0");

        assertThat(refreshed.batch().status()).isEqualTo(KnowledgeInitializationBatchStatus.COMPLETE);
        assertThat(refreshed.items()).singleElement()
            .satisfies(saved -> assertThat(saved.status())
                .isEqualTo(KnowledgeInitializationItemStatus.APPROVED));
    }

    @Test
    void refreshBlocksWholeBatchWhenApprovedSourceHashDrifts() {
        KnowledgeInitializationBatch batch = batch(KnowledgeRiskLevel.MEDIUM);
        KnowledgeInitializationItem item = batchItem(KnowledgeRiskLevel.MEDIUM);
        when(batches.findByTenantIdAndBatchCode("t-1", "foundation-f1-1.0.0"))
            .thenReturn(Optional.of(batch));
        when(items.findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc("t-1", 10L))
            .thenReturn(List.of(item));
        when(sourceVersions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(sourceVersion("f")));
        when(sourceApprovals.findByTenantIdAndSourceVersionId("t-1", 9L))
            .thenReturn(Optional.of(sourceApproval("a")));
        when(classifications.findByTenantIdAndId("t-1", 88L))
            .thenReturn(Optional.of(classification(CandidateReviewStatus.APPROVED)));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeInitializationBatchView refreshed = service.refresh("foundation-f1-1.0.0");

        assertThat(refreshed.batch().status()).isEqualTo(KnowledgeInitializationBatchStatus.BLOCKED);
        assertThat(refreshed.items()).singleElement()
            .satisfies(saved -> assertThat(saved.status())
                .isEqualTo(KnowledgeInitializationItemStatus.BLOCKED));
    }

    private void seedCandidate() {
        KnowledgeAssetVersion candidate = candidate();
        CandidateClassification classification = new CandidateClassification(
            88L, "t-1", "tenant:t-1", 1L, 22L, null,
            CandidateClassificationType.NEW_ASSET, CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW,
            "b".repeat(64), "新建", "无现行版本", Instant.EPOCH, "steward", Instant.EPOCH, "steward");
        when(versions.findByTenantIdAndIdentityIdAndVersionNo("t-1", 1L, "1.0.0"))
            .thenReturn(Optional.of(candidate));
        when(classifications.findByTenantIdAndCandidateVersionId("t-1", 22L))
            .thenReturn(Optional.of(classification));
        when(productionCandidates.findByTenantIdAndCandidateRefIn(
            "t-1", List.of("kv:1:1.0.0"))).thenReturn(List.of(
                new KnowledgeProductionCandidate(
                    3L, "t-1", "job-1", "DATA_ELEMENT.BP", "b".repeat(64),
                    "kv:1:1.0.0", KnowledgeRiskLevel.MEDIUM, Instant.EPOCH, "steward")));
        when(productionJobs.findByTenantIdAndJobCode("t-1", "job-1")).thenReturn(Optional.of(
            new KnowledgeProductionJob(
                2L, "t-1", "job-1", "source-version:9", VersionedAssetType.FIELD_CATALOG,
                KnowledgeProducer.MANUAL, TargetPipeline.PLATFORM_SOURCE,
                com.medkernel.engine.knowledge.production.KnowledgeDomain.GENERAL, null,
                ProductionJobStatus.RUNNING, 1, "{}", Instant.EPOCH, "steward",
                Instant.EPOCH, "steward", "trace")));
        when(sourceVersions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(
            new SourceVersion(
                9L, "t-1", 7L, "official-1", Instant.EPOCH, "a".repeat(64),
                "file:///managed/source.pdf", "zh-CN", Instant.EPOCH, "steward")));
        when(sourceFragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L))
            .thenReturn(List.of(new SourceFragment(
                11L, "t-1", 9L, "root/0", "根条款", "权威来源片段",
                "d".repeat(64), Instant.EPOCH)));
        when(items.findCompletedCanonicalIds("t-1")).thenReturn(List.of());
        when(items.findCompletedHistory("t-1", "DATA_ELEMENT.BP")).thenReturn(List.of());
    }

    private KnowledgeAssetVersion candidate() {
        return new KnowledgeAssetVersion(
            22L, "t-1", 1L, "1.0.0", "初始数据元", 7L, 9L,
            "b".repeat(64), "root/0", com.medkernel.engine.knowledge.KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW,
            KnowledgeRiskLevel.MEDIUM, SourceAuthorityLevel.A_REGULATION, null, null, null,
            "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE, "version:22",
            null, null, null, null, null, null, null, null,
            Instant.EPOCH, "steward", Instant.EPOCH, "steward", 12, null);
    }

    private SourceVersion sourceVersion(String hashPrefix) {
        return new SourceVersion(
            9L, "t-1", 7L, "official-1", Instant.EPOCH, hashPrefix.repeat(64),
            "file:///managed/source.pdf", "zh-CN", Instant.EPOCH, "steward");
    }

    private SourceVersionApproval sourceApproval(String hashPrefix) {
        return new SourceVersionApproval(
            1L, "t-1", 9L, hashPrefix.repeat(64), SourceVersionApprovalStatus.APPROVED,
            "reviewer-a", Instant.EPOCH, "批准", Instant.EPOCH, "reviewer-a");
    }

    private CandidateClassification classification(CandidateReviewStatus status) {
        return new CandidateClassification(
            88L, "t-1", "tenant:t-1", 1L, 22L, null,
            CandidateClassificationType.NEW_ASSET, status,
            "b".repeat(64), "新建", "无现行版本", Instant.EPOCH, "steward",
            Instant.EPOCH, "steward");
    }

    private KnowledgeInitializationBatchDraftRequest request() {
        return request(
            "urn:medkernel:data-element",
            "1.0.0",
            InitializationChangeType.NEW);
    }

    private KnowledgeInitializationBatchDraftRequest request(
            String namespace,
            String assetVersion,
            InitializationChangeType changeType) {
        return new KnowledgeInitializationBatchDraftRequest(
            "foundation-f1-1.0.0",
            InitializationReleaseType.FOUNDATION,
            "1.0.0",
            null,
            InitializationPhase.F1,
            1,
            1,
            Set.copyOf(Arrays.asList(FoundationCoverageDimension.values())),
            "template-v1",
            null,
            "基础数据元首发",
            "init-foundation-f1-1.0.0",
            List.of(new KnowledgeInitializationEntryRequest(
                "KNOWGEN-26", "DATA_ELEMENT.BP", namespace, assetVersion,
                "kv:1:1.0.0", List.of(), null, null, null,
                "APPROVED_SOURCE_ONLY", "RISK_TIERED_REVIEW", "golden:bp",
                "platform-knowledge-governor", "context-and-rule-runtime", "rollback:foundation-1.0.0",
                changeType, null, null)));
    }

    private KnowledgeInitializationBatch batch(KnowledgeRiskLevel risk) {
        return new KnowledgeInitializationBatch(
            10L, "t-1", "foundation-f1-1.0.0", InitializationReleaseType.FOUNDATION,
            "1.0.0", null, InitializationPhase.F8, KnowledgeInitializationBatchStatus.IN_REVIEW,
            "c".repeat(64), "d".repeat(64), "e".repeat(64), 1, 1,
            risk == KnowledgeRiskLevel.LOW ? 1 : 0,
            risk == KnowledgeRiskLevel.MEDIUM ? 1 : 0,
            risk == KnowledgeRiskLevel.HIGH ? 1 : 0,
            "[]", "template-v1", null, "基础批次", "init-foundation-f1-1.0.0",
            null, null, Instant.EPOCH, "reviewer", Instant.EPOCH, "reviewer");
    }

    private KnowledgeInitializationItem batchItem(KnowledgeRiskLevel risk) {
        return new KnowledgeInitializationItem(
            20L, "t-1", 10L, 1, "KNOWGEN-26", VersionedAssetType.FIELD_CATALOG,
            "DATA_ELEMENT.BP", "urn:medkernel:data-element", "1.0.0", 9L, "a".repeat(64),
            "kv:1:1.0.0", 88L, "b".repeat(64), risk, "N", "[]", "{}",
            InitializationChangeType.NEW, null, null, KnowledgeInitializationItemStatus.PENDING_REVIEW,
            Instant.EPOCH, "reviewer", Instant.EPOCH, "reviewer");
    }

    private KnowledgeInitializationItem completedItem(String namespace, String assetVersion) {
        KnowledgeInitializationItem item = batchItem(KnowledgeRiskLevel.MEDIUM);
        return new KnowledgeInitializationItem(
            19L, item.tenantId(), 9L, item.sequenceNo(), item.catalogCode(), item.assetType(),
            item.canonicalId(), namespace, assetVersion, item.sourceVersionId(), item.sourceHash(),
            item.candidateRef(), 87L, item.candidateContentHash(), item.riskLevel(),
            item.generatedByModelFlag(), item.dependenciesJson(), item.governanceJson(),
            InitializationChangeType.NEW, null, null, KnowledgeInitializationItemStatus.APPROVED,
            item.createdAt(), item.createdBy(), item.updatedAt(), item.updatedBy());
    }

    private KnowledgeInitializationItem withItemStatus(
            KnowledgeInitializationItem item,
            KnowledgeInitializationItemStatus status) {
        return new KnowledgeInitializationItem(
            item.id(), item.tenantId(), item.batchId(), item.sequenceNo(), item.catalogCode(),
            item.assetType(), item.canonicalId(), item.namespace(), item.assetVersion(),
            item.sourceVersionId(), item.sourceHash(), item.candidateRef(),
            item.candidateClassificationId(), item.candidateContentHash(), item.riskLevel(),
            item.generatedByModelFlag(), item.dependenciesJson(), item.governanceJson(),
            item.changeType(), item.replacementCanonicalId(), item.effectiveTo(), status,
            item.createdAt(), item.createdBy(), item.updatedAt(), item.updatedBy());
    }

    private KnowledgeInitializationBatch withBatchPhase(
            KnowledgeInitializationBatch batch,
            InitializationPhase phase) {
        return new KnowledgeInitializationBatch(
            batch.id(), batch.tenantId(), batch.batchCode(), batch.releaseType(),
            batch.releaseVersion(), batch.foundationReleaseVersion(), phase, batch.status(),
            batch.sourceManifestHash(), batch.candidateManifestHash(), batch.overallHash(),
            batch.sourceCount(), batch.candidateCount(), batch.lowCount(), batch.mediumCount(),
            batch.highCount(), batch.coverageJson(), batch.templateVersion(), batch.modelVersion(),
            batch.summary(), batch.idempotencyKey(), batch.lastBulkIdempotencyKey(),
            batch.lastBulkAt(), batch.createdAt(), batch.createdBy(), batch.updatedAt(),
            batch.updatedBy());
    }
}
