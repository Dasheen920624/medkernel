package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeVersionService 单元测试。重点覆盖 activate 原子替换 + withdraw 状态机门禁。
 */
class KnowledgeVersionServiceTest {

    private KnowledgeIdentityRepository identityRepo;
    private KnowledgeAssetVersionRepository versionRepo;
    private KnowledgeSupersessionRepository supersessionRepo;
    private CitationRepository citationRepo;
    private SourceDocumentRepository sourceDocRepo;
    private KnowledgeProjectionRefreshPort projectionRefreshPort;
    private CandidateClassificationRepository candidateClassificationRepo;
    private ReviewAssignmentRepository reviewAssignmentRepo;
    private KnowledgeVersionService service;

    @BeforeEach
    void setUp() {
        identityRepo = Mockito.mock(KnowledgeIdentityRepository.class);
        versionRepo = Mockito.mock(KnowledgeAssetVersionRepository.class);
        supersessionRepo = Mockito.mock(KnowledgeSupersessionRepository.class);
        citationRepo = Mockito.mock(CitationRepository.class);
        sourceDocRepo = Mockito.mock(SourceDocumentRepository.class);
        projectionRefreshPort = Mockito.mock(KnowledgeProjectionRefreshPort.class);
        candidateClassificationRepo = Mockito.mock(CandidateClassificationRepository.class);
        reviewAssignmentRepo = Mockito.mock(ReviewAssignmentRepository.class);
        service = new KnowledgeVersionService(
            identityRepo, versionRepo, supersessionRepo, citationRepo, sourceDocRepo, projectionRefreshPort,
            candidateClassificationRepo, reviewAssignmentRepo);
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-99"));

        // 默认 save 返回参数，方便断言保留字段
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(identityRepo.save(any(KnowledgeIdentity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supersessionRepo.save(any(KnowledgeSupersession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(candidateClassificationRepo.save(any(CandidateClassification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewAssignmentRepo.save(any(ReviewAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void activateFirstVersionTransitionsToActiveAndWritesSupersession() {
        // 给一个无 active 版本的身份 + 一个 UNDER_REVIEW 候选版本
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion candidate = version(10L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(versionRepo.findActiveByIdentity("t-1", 1L)).thenReturn(Optional.empty());
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 10L))
            .thenReturn(List.of(citation(10L)));

        KnowledgeAssetVersion activated = service.activate(1L, 10L, null);

        // 1) 目标版本变 ACTIVE
        assertThat(activated.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);
        assertThat(activated.activatedAt()).isNotNull();
        assertThat(activated.reviewedBy()).isEqualTo("u-99");

        // 2) 没有旧 ACTIVE，因此不会保存 SUPERSEDED
        verify(versionRepo, times(1)).save(any(KnowledgeAssetVersion.class));

        // 3) 身份 current_version_id 指向新版
        ArgumentCaptor<KnowledgeIdentity> idCap = ArgumentCaptor.forClass(KnowledgeIdentity.class);
        verify(identityRepo).save(idCap.capture());
        assertThat(idCap.getValue().currentVersionId()).isEqualTo(10L);

        // 4) supersession 记录 type=ACTIVATE
        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionType()).isEqualTo(SupersessionType.ACTIVATE);
        assertThat(spCap.getValue().oldVersionId()).isNull();
        assertThat(spCap.getValue().newVersionId()).isEqualTo(10L);
        assertThat(spCap.getValue().transitionedBy()).isEqualTo("u-99");
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 10L, "u-99", "trace");
    }

    @Test
    void activateReplacingPriorActiveDemotesItToSuperseded() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion oldActive = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        KnowledgeAssetVersion newCandidate = version(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L)).thenReturn(Optional.of(newCandidate));
        when(versionRepo.findActiveByIdentity("t-1", 1L)).thenReturn(Optional.of(oldActive));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));

        service.activate(1L, 11L, "新版指南更新");

        ArgumentCaptor<KnowledgeAssetVersion> vCap = ArgumentCaptor.forClass(KnowledgeAssetVersion.class);
        verify(versionRepo, times(2)).save(vCap.capture());

        // 第一次 save 应该是旧版变 SUPERSEDED
        KnowledgeAssetVersion superseded = vCap.getAllValues().get(0);
        assertThat(superseded.id()).isEqualTo(5L);
        assertThat(superseded.status()).isEqualTo(KnowledgeVersionStatus.SUPERSEDED);
        assertThat(superseded.supersededAt()).isNotNull();
        assertThat(superseded.effectiveTo()).isNotNull();

        // 第二次是新版变 ACTIVE
        KnowledgeAssetVersion activated = vCap.getAllValues().get(1);
        assertThat(activated.id()).isEqualTo(11L);
        assertThat(activated.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);

        // supersession type 应该是 REPLACE
        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionType()).isEqualTo(SupersessionType.REPLACE);
        assertThat(spCap.getValue().oldVersionId()).isEqualTo(5L);
        assertThat(spCap.getValue().newVersionId()).isEqualTo(11L);
        assertThat(spCap.getValue().transitionReason()).isEqualTo("新版指南更新");
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 11L, "u-99", "trace");
    }

    @Test
    void activateRefreshesKnowledgeGraphAndSearchProjectionAfterPublication() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion candidate = version(10L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(versionRepo.findActiveByIdentity("t-1", 1L)).thenReturn(Optional.empty());
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 10L))
            .thenReturn(List.of(citation(10L)));

        KnowledgeAssetVersion activated = service.activate(1L, 10L, "发布新版知识资产");

        assertThat(activated.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 10L, "u-99", "trace");
    }

    @Test
    void activateHigherAuthorityCandidateRecordsArbitrationWithoutManualReason() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion oldActive =
            version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.D_HOSPITAL);
        KnowledgeAssetVersion newCandidate =
            version(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.A_REGULATION);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L)).thenReturn(Optional.of(newCandidate));
        when(versionRepo.findActiveByIdentity("t-1", 1L)).thenReturn(Optional.of(oldActive));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));

        service.activate(1L, 11L, null);

        ArgumentCaptor<KnowledgeAssetVersion> vCap = ArgumentCaptor.forClass(KnowledgeAssetVersion.class);
        verify(versionRepo, times(2)).save(vCap.capture());
        KnowledgeAssetVersion activated = vCap.getAllValues().get(1);
        assertThat(activated.conflictArbitration()).contains("A 法规").contains("D 院内");

        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionReason()).contains("可信分级裁决");
    }

    @Test
    void activateRejectsLowAuthorityOverrideWithoutReason() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion oldActive =
            version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.A_REGULATION);
        KnowledgeAssetVersion newCandidate =
            version(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.D_HOSPITAL);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L)).thenReturn(Optional.of(newCandidate));
        when(versionRepo.findActiveByIdentity("t-1", 1L)).thenReturn(Optional.of(oldActive));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));

        assertThatThrownBy(() -> service.activate(1L, 11L, "  "))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTHORITY_OVERRIDE_DENIED);
        verify(versionRepo, never()).save(any());
        verify(supersessionRepo, never()).save(any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void activateAllowsLowAuthorityOverrideWithExplicitReasonAndRecordsArbitration() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion oldActive =
            version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.A_REGULATION);
        KnowledgeAssetVersion newCandidate =
            version(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.D_HOSPITAL);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L)).thenReturn(Optional.of(newCandidate));
        when(versionRepo.findActiveByIdentity("t-1", 1L)).thenReturn(Optional.of(oldActive));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));

        service.activate(1L, 11L, "院内药事会已审核本院禁忌证差异");

        ArgumentCaptor<KnowledgeAssetVersion> vCap = ArgumentCaptor.forClass(KnowledgeAssetVersion.class);
        verify(versionRepo, times(2)).save(vCap.capture());
        assertThat(vCap.getAllValues().get(1).conflictArbitration()).contains("低阶来源覆盖高阶来源");

        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionReason())
            .contains("院内药事会已审核本院禁忌证差异")
            .contains("低阶来源覆盖高阶来源");
    }

    @Test
    void activateRejectsNonActivatableVersion() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion draft = version(10L, 1L, KnowledgeVersionStatus.DRAFT, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.activate(1L, 10L, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
        verify(versionRepo, never()).save(any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void activateRejectsCrossIdentityVersion() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion otherIdentityVersion = version(10L, 999L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(otherIdentityVersion));

        assertThatThrownBy(() -> service.activate(1L, 10L, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void activateHighRiskRequiresReason() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion highRisk = version(10L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.HIGH);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(highRisk));

        assertThatThrownBy(() -> service.activate(1L, 10L, "  "))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void activateRejectsVersionWithoutCitation() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion candidate = version(10L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.activate(1L, 10L, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.KNOWLEDGE_CITATION_REQUIRED);
        verify(versionRepo, never()).save(any());
    }

    @Test
    void activateRejectsWithdrawnIdentity() {
        KnowledgeIdentity identity = new KnowledgeIdentity(
            1L, "t-1", "DRUG.X", KnowledgeDomain.DRUG, "已撤回主题", null, null,
            KnowledgeIdentityStatus.WITHDRAWN, null,
            Instant.now(), "u", Instant.now(), "u"
        );
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));

        assertThatThrownBy(() -> service.activate(1L, 99L, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void withdrawActiveVersionTransitionsToWithdrawn() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.HIGH);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(active));

        KnowledgeAssetVersion withdrawn = service.withdraw(1L, 5L, "上游召回紧急通知");

        assertThat(withdrawn.status()).isEqualTo(KnowledgeVersionStatus.WITHDRAWN);
        assertThat(withdrawn.withdrawnAt()).isNotNull();
        assertThat(withdrawn.withdrawnReason()).isEqualTo("上游召回紧急通知");

        // identity.current_version_id 应该被置 null
        ArgumentCaptor<KnowledgeIdentity> idCap = ArgumentCaptor.forClass(KnowledgeIdentity.class);
        verify(identityRepo).save(idCap.capture());
        assertThat(idCap.getValue().currentVersionId()).isNull();

        // supersession type=WITHDRAW
        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionType()).isEqualTo(SupersessionType.WITHDRAW);
    }

    @Test
    void withdrawRequiresReason() {
        // 没有跑到锁定步骤就被拒
        assertThatThrownBy(() -> service.withdraw(1L, 5L, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(identityRepo, never()).findByTenantIdAndIdForUpdate(any(), any());
    }

    @Test
    void withdrawRejectsNonActiveVersion() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion draft = version(5L, 1L, KnowledgeVersionStatus.DRAFT, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.withdraw(1L, 5L, "原因"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void listByIdentityRequiresIdentityExists() {
        when(identityRepo.findByTenantIdAndId("t-1", 99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listByIdentity(99L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void classifyCandidateWithStandardRequestUsesPathIdentity() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(sourceDocument(7L, SourceAuthorityLevel.B_GUIDELINE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L)).thenReturn(List.of());
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(candidateClassificationRepo.save(any(CandidateClassification.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeCandidateResponse response = service.classifyCandidate(1L, versionCreateRequest("v2"));

        KnowledgeAssetVersion created = response.candidates().get(0);
        assertThat(created.identityId()).isEqualTo(1L);
        assertThat(created.versionNo()).isEqualTo("v2");
        assertThat(created.status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
        assertThat(created.contentHash()).isNotBlank();
        assertThat(created.authorityLevel()).isEqualTo(SourceAuthorityLevel.B_GUIDELINE);
        assertThat(created.gradeQuality()).isEqualTo(GradeEvidenceQuality.HIGH);
        assertThat(created.gradeStrength()).isEqualTo(GradeRecommendationStrength.STRONG);
        assertThat(created.createdBy()).isEqualTo("u-99");
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> {
                assertThat(item.classification()).isEqualTo(CandidateClassificationType.NEW_ASSET);
                assertThat(item.orgPath()).isEqualTo("tenant:t-1");
            });
        ArgumentCaptor<ReviewAssignment> assignment = ArgumentCaptor.forClass(ReviewAssignment.class);
        verify(reviewAssignmentRepo).save(assignment.capture());
        assertThat(assignment.getValue().orgPath()).isEqualTo("tenant:t-1");
    }

    @Test
    void classifyCandidateStoresCanonicalSha256() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(sourceDocument(10L, SourceAuthorityLevel.C_CONSENSUS_LITERATURE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L)).thenReturn(List.of());
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(candidateClassificationRepo.save(any(CandidateClassification.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeAssetVersion created = service.classifyCandidate(1L,
            versionCreateRequestWithTenant("t-1", 10L, 20L, "v2", "真实指南内容")).candidates().get(0);

        assertThat(created.contentHash()).isEqualTo(sha256("真实指南内容"));
        assertThat(created.contentHash()).matches("[0-9a-f]{64}");
        assertThat(created.authorityLevel()).isEqualTo(SourceAuthorityLevel.C_CONSENSUS_LITERATURE);
        assertThat(created.gradeQuality()).isEqualTo(GradeEvidenceQuality.HIGH);
        assertThat(created.gradeStrength()).isEqualTo(GradeRecommendationStrength.STRONG);
    }

    @Test
    void classifyCandidateRejectsBlankContentInsteadOfHashingEmptyString() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(sourceDocument(10L, SourceAuthorityLevel.C_CONSENSUS_LITERATURE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.classifyCandidate(1L,
            versionCreateRequestWithTenant("t-1", 10L, 20L, "v2", "   ")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(versionRepo, never()).save(any());
    }

    @Test
    void submitDraftTransitionsToUnderReviewAndKeepsVersionFields() {
        KnowledgeAssetVersion draft = version(10L, 1L, KnowledgeVersionStatus.DRAFT, KnowledgeRiskLevel.HIGH);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(draft));

        KnowledgeAssetVersion submitted = service.submit(1L, 10L, actionRequest("t-1"));

        assertThat(submitted.status()).isEqualTo(KnowledgeVersionStatus.UNDER_REVIEW);
        assertThat(submitted.riskLevel()).isEqualTo(KnowledgeRiskLevel.HIGH);
        assertThat(submitted.contentHash()).isEqualTo(draft.contentHash());
        assertThat(submitted.identityId()).isEqualTo(1L);
        assertThat(submitted.updatedBy()).isEqualTo("u-99");
        verify(versionRepo).save(any(KnowledgeAssetVersion.class));
    }

    @Test
    void submitUnderReviewIsIdempotent() {
        KnowledgeAssetVersion underReview = version(10L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(underReview));

        KnowledgeAssetVersion result = service.submit(1L, 10L, actionRequest("t-1"));

        assertThat(result).isSameAs(underReview);
        verify(versionRepo, never()).save(any());
    }

    @Test
    void submitRejectsCrossIdentityVersion() {
        KnowledgeAssetVersion otherIdentityVersion = version(10L, 2L, KnowledgeVersionStatus.DRAFT, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(otherIdentityVersion));

        assertThatThrownBy(() -> service.submit(1L, 10L, actionRequest("t-1")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
        verify(versionRepo, never()).save(any());
    }

    @Test
    void submitRejectsMismatchedTenantContext() {
        assertThatThrownBy(() -> service.submit(1L, 10L, actionRequest("t-2")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);
        verify(versionRepo, never()).findByTenantIdAndId(any(), any());
    }

    @Test
    void replayVersionRejectsVersionNotBelongingToIdentity() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.findByTenantIdAndId("t-1", 10L))
            .thenReturn(Optional.of(version(10L, 2L, KnowledgeVersionStatus.SUPERSEDED, KnowledgeRiskLevel.LOW)));

        assertThatThrownBy(() -> service.replayVersion(1L, 10L, "pkg-2026.06", "snap-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void replayVersionReturnsHistoricalMarkerWithoutRequiringActiveStatus() {
        KnowledgeAssetVersion superseded = version(10L, 1L, KnowledgeVersionStatus.SUPERSEDED, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(superseded));

        KnowledgeReplayResponse replay = service.replayVersion(1L, 10L, "pkg-2026.06", "snap-1");

        assertThat(replay.identityId()).isEqualTo(1L);
        assertThat(replay.versionId()).isEqualTo(10L);
        assertThat(replay.historicalVersion()).isTrue();
        assertThat(replay.status()).isEqualTo(KnowledgeVersionStatus.SUPERSEDED);
        assertThat(replay.packageVersion()).isEqualTo("pkg-2026.06");
        assertThat(replay.snapshotId()).isEqualTo("snap-1");
    }

    @Test
    void listCandidatesReturnsAvailableEmptyWorkflowWhenNoPendingCandidates() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));

        KnowledgeCandidateResponse response = service.listCandidates(1L);

        assertThat(response.identityId()).isEqualTo(1L);
        assertThat(response.available()).isTrue();
        assertThat(response.reasonCode()).isEqualTo("OK");
        assertThat(response.candidates()).isEmpty();
        assertThat(response.classifications()).isEmpty();
    }

    @Test
    void classifyDuplicateCandidateRecordsBasisWithoutCreatingReviewTodoOrVersion() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(sourceDocument(7L, SourceAuthorityLevel.B_GUIDELINE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L))
            .thenReturn(List.of(active));

        KnowledgeCandidateResponse response = service.classifyCandidate(1L, versionCreateRequestWithContent(
            "duplicate-v2", "知识版本夹具内容-t-1-5"));

        assertThat(response.available()).isTrue();
        assertThat(response.reasonCode()).isEqualTo("DUPLICATE");
        assertThat(response.candidates()).isEmpty();
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> {
                assertThat(item.classification()).isEqualTo(CandidateClassificationType.DUPLICATE);
                assertThat(item.reviewStatus()).isEqualTo(CandidateReviewStatus.DUPLICATE_SKIPPED);
                assertThat(item.basis()).contains("content_hash").contains(active.contentHash());
                assertThat(item.activeVersionId()).isEqualTo(5L);
                assertThat(item.candidateVersionId()).isNull();
            });
        verify(versionRepo, never()).save(any());
        verify(reviewAssignmentRepo, never()).save(any());
    }

    @Test
    void classifyNewVersionCreatesPendingReviewCandidateWithoutActivatingIt() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(sourceDocument(7L, SourceAuthorityLevel.B_GUIDELINE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L))
            .thenReturn(List.of(active));
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> {
            KnowledgeAssetVersion candidate = inv.getArgument(0);
            return new KnowledgeAssetVersion(
                22L, candidate.tenantId(), candidate.identityId(), candidate.versionNo(), candidate.versionLabel(),
                candidate.sourceDocumentId(), candidate.sourceVersionId(), candidate.contentHash(), candidate.anchors(),
                candidate.status(), candidate.riskLevel(), candidate.authorityLevel(), candidate.gradeQuality(),
                candidate.gradeStrength(), candidate.conflictArbitration(), candidate.effectiveFrom(), candidate.effectiveTo(),
                candidate.reviewedBy(), candidate.reviewedAt(), candidate.activatedAt(), candidate.supersededAt(),
                candidate.withdrawnAt(), candidate.withdrawnReason(), candidate.createdAt(), candidate.createdBy(),
                candidate.updatedAt(), candidate.updatedBy());
        });

        KnowledgeCandidateResponse response = service.classifyCandidate(1L, versionCreateRequestWithContent(
            "regulation-v2", "国家法规更新后的真实内容"));

        assertThat(response.reasonCode()).isEqualTo("SAME_IDENTITY_NEW_VERSION");
        assertThat(response.candidates()).singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.id()).isEqualTo(22L);
                assertThat(candidate.status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
                assertThat(candidate.activatedAt()).isNull();
            });
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> {
                assertThat(item.classification()).isEqualTo(CandidateClassificationType.SAME_IDENTITY_NEW_VERSION);
                assertThat(item.reviewStatus()).isEqualTo(CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
                assertThat(item.activeVersionId()).isEqualTo(5L);
                assertThat(item.candidateVersionId()).isEqualTo(22L);
                assertThat(item.diffSummary()).contains("当前 ACTIVE").contains("候选");
            });
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
        verify(reviewAssignmentRepo).save(any(ReviewAssignment.class));
    }

    @Test
    void reviewCandidateHasTransactionalBoundaryForApprovalReplacement() throws NoSuchMethodException {
        Transactional transactional = KnowledgeVersionService.class
            .getDeclaredMethod("reviewCandidate", Long.class, KnowledgeCandidateReviewRequest.class)
            .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void approveCandidateDelegatesToAtomicActivationFlow() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        KnowledgeAssetVersion candidate = version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));
        when(versionRepo.findActiveByIdentity("t-1", 1L)).thenReturn(Optional.of(active));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 22L))
            .thenReturn(List.of(citation(22L)));

        KnowledgeCandidateResponse response = service.reviewCandidate(88L, candidateReviewRequest("t-1"));

        assertThat(response.reasonCode()).isEqualTo("APPROVED");
        assertThat(response.candidates()).singleElement()
            .satisfies(approved -> assertThat(approved.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE));
        verify(supersessionRepo).save(any(KnowledgeSupersession.class));
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 22L, "u-99", "trace");
    }

    @Test
    void rejectCandidateMarksVersionRejectedWithoutActivationSideEffects() {
        KnowledgeAssetVersion candidate = version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.CONFLICT,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));

        KnowledgeCandidateReviewRequest rejectRequest = new KnowledgeCandidateReviewRequest(
            "req-1", "trace-1", "t-1", null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.review"), "pkg-2026.06",
            KnowledgeCandidateReviewDecision.REJECT, "来源冲突，退回补证"
        );
        KnowledgeCandidateResponse response = service.reviewCandidate(88L, rejectRequest);

        assertThat(response.reasonCode()).isEqualTo("REJECTED");
        assertThat(response.candidates()).singleElement()
            .satisfies(rejected -> assertThat(rejected.status()).isEqualTo(KnowledgeVersionStatus.REJECTED));
        ArgumentCaptor<ReviewAssignment> assignment = ArgumentCaptor.forClass(ReviewAssignment.class);
        verify(reviewAssignmentRepo).save(assignment.capture());
        assertThat(assignment.getValue().reason()).isEqualTo("来源冲突，退回补证");
        assertThat(assignment.getValue().decision()).isEqualTo(KnowledgeCandidateReviewDecision.REJECT);
        verify(supersessionRepo, never()).save(any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void diffCandidateReturnsStoredConflictViewWithoutActivatingCandidate() {
        KnowledgeAssetVersion candidate = version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.CONFLICT,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));

        KnowledgeCandidateResponse response = service.diffCandidate(88L);

        assertThat(response.reasonCode()).isEqualTo("CONFLICT");
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> {
                assertThat(item.classification()).isEqualTo(CandidateClassificationType.CONFLICT);
                assertThat(item.diffSummary()).contains("当前 ACTIVE 与候选对照");
            });
        assertThat(response.candidates()).singleElement()
            .satisfies(item -> assertThat(item.status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW));
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void diffDuplicateCandidateReturnsClassificationWithoutLookingUpMissingVersion() {
        CandidateClassification classification = classification(
            89L,
            1L,
            null,
            5L,
            CandidateClassificationType.DUPLICATE,
            CandidateReviewStatus.DUPLICATE_SKIPPED);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 89L)).thenReturn(Optional.of(classification));

        KnowledgeCandidateResponse response = service.diffCandidate(89L);

        assertThat(response.reasonCode()).isEqualTo("DUPLICATE");
        assertThat(response.candidates()).isEmpty();
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> assertThat(item.reviewStatus()).isEqualTo(CandidateReviewStatus.DUPLICATE_SKIPPED));
        verify(versionRepo, never()).findByTenantIdAndId(any(), any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void reviewCandidateReturnsNotFoundWhileKnow02StorageIsAbsent() {
        assertThatThrownBy(() -> service.reviewCandidate(88L, candidateReviewRequest("t-1")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void diffCandidateReturnsNotFoundWhileKnow02StorageIsAbsent() {
        assertThatThrownBy(() -> service.diffCandidate(88L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void requiresTenantContext() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.empty(), null));
        assertThatThrownBy(() -> service.activate(1L, 10L, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);
    }

    @Test
    void classifyCandidateInCustomerTenantDoesNotWriteBackToPlatformTenant() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-hospital"), "hospital-admin"));
        KnowledgeIdentity identity = new KnowledgeIdentity(
            1L, "t-hospital", "DRUG.X", KnowledgeDomain.DRUG, "医院定制主题", null, null,
            KnowledgeIdentityStatus.ACTIVE, null,
            Instant.now(), "hospital-admin", Instant.now(), "hospital-admin"
        );
        when(identityRepo.findByTenantIdAndId("t-hospital", 1L)).thenReturn(Optional.of(identity));
        when(sourceDocRepo.findByTenantIdAndId("t-hospital", 10L)).thenReturn(Optional.of(sourceDocument("t-hospital", 10L, SourceAuthorityLevel.D_HOSPITAL)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-hospital", 1L))
            .thenReturn(List.of());
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(candidateClassificationRepo.save(any(CandidateClassification.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeAssetVersion saved = service.classifyCandidate(1L,
            versionCreateRequestWithTenant("t-hospital", 10L, 20L, "hospital-v1", "医院本地定制内容"))
            .candidates()
            .get(0);

        assertThat(saved.tenantId()).isEqualTo("t-hospital");
        assertThat(saved.createdBy()).isEqualTo("hospital-admin");
        verify(identityRepo, never()).findByTenantIdAndId("t-1", 1L);
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L);
    }

    @Test
    void listByIdentityFallsBackToPlatformIdentityWhenCustomerHasNoLocalOverride() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-hospital"), "doctor"));
        KnowledgeIdentity platformIdentity = identity(100L, "t-1", "DRUG.X", null);
        KnowledgeAssetVersion platformVersion =
            version(900L, "t-1", 100L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-hospital", 100L)).thenReturn(Optional.empty());
        when(identityRepo.findByTenantIdAndId("t-1", 100L)).thenReturn(Optional.of(platformIdentity));
        when(identityRepo.findByTenantIdAndIdentityCode("t-hospital", "DRUG.X")).thenReturn(Optional.empty());
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 100L))
            .thenReturn(List.of(platformVersion));

        List<KnowledgeAssetVersion> versions = service.listByIdentity(100L);

        assertThat(versions).containsExactly(platformVersion);
    }

    @Test
    void listByIdentityPrefersLocalIdentityWithSameCodeOverPlatformIdentity() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-hospital"), "doctor"));
        KnowledgeIdentity platformIdentity = identity(100L, "t-1", "DRUG.X", null);
        KnowledgeIdentity localIdentity = identity(200L, "t-hospital", "DRUG.X", null);
        KnowledgeAssetVersion localVersion =
            version(901L, "t-hospital", 200L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-hospital", 100L)).thenReturn(Optional.empty());
        when(identityRepo.findByTenantIdAndId("t-1", 100L)).thenReturn(Optional.of(platformIdentity));
        when(identityRepo.findByTenantIdAndIdentityCode("t-hospital", "DRUG.X")).thenReturn(Optional.of(localIdentity));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-hospital", 200L))
            .thenReturn(List.of(localVersion));

        List<KnowledgeAssetVersion> versions = service.listByIdentity(100L);

        assertThat(versions).containsExactly(localVersion);
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 100L);
    }

    // ─── helpers ──────────────────────────────────────────────

    private KnowledgeIdentity identity(Long id, Long currentVersionId) {
        return identity(id, "t-1", "DRUG.X", currentVersionId);
    }

    private KnowledgeIdentity identity(Long id, String tenantId, String identityCode, Long currentVersionId) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            id, tenantId, identityCode, KnowledgeDomain.DRUG, "测试主题", null, null,
            KnowledgeIdentityStatus.ACTIVE, currentVersionId,
            now, "init", now, "init"
        );
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, KnowledgeVersionStatus status, KnowledgeRiskLevel risk) {
        return version(id, "t-1", identityId, status, risk, SourceAuthorityLevel.B_GUIDELINE);
    }

    private KnowledgeAssetVersion version(Long id, String tenantId, Long identityId,
                                          KnowledgeVersionStatus status, KnowledgeRiskLevel risk) {
        return version(id, tenantId, identityId, status, risk, SourceAuthorityLevel.B_GUIDELINE);
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, KnowledgeVersionStatus status,
                                          KnowledgeRiskLevel risk, SourceAuthorityLevel authorityLevel) {
        return version(id, "t-1", identityId, status, risk, authorityLevel);
    }

    private KnowledgeAssetVersion version(Long id, String tenantId, Long identityId,
                                          KnowledgeVersionStatus status, KnowledgeRiskLevel risk,
                                          SourceAuthorityLevel authorityLevel) {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(
            id, tenantId, identityId, "v1", "label",
            null, null, sha256("知识版本夹具内容-" + tenantId + "-" + id), null,
            status, risk,
            authorityLevel, null, null, null,
            null, null, null, null,
            status == KnowledgeVersionStatus.ACTIVE ? now : null, null,
            null, null,
            now, "init", now, "init"
        );
    }

    private Citation citation(Long versionId) {
        return new Citation(1L, "t-1", versionId, 100L, CitationRelation.SUPPORTS, 100, null, null, Instant.now(), "init");
    }

    private KnowledgeVersionCreateRequest versionCreateRequest(String versionNo) {
        return versionCreateRequestWithContent(versionNo, "真实指南内容");
    }

    private KnowledgeVersionCreateRequest versionCreateRequestWithContent(String versionNo, String content) {
        return versionCreateRequestWithTenant("t-1", 7L, 8L, versionNo, content);
    }

    private KnowledgeVersionCreateRequest versionCreateRequestWithTenant(String tenantId, Long sourceDocumentId,
            Long sourceVersionId, String versionNo, String content) {
        return new KnowledgeVersionCreateRequest(
            "req-1", "trace-1", tenantId, null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.write"), "pkg-2026.06",
            versionNo, "2026 版", sourceDocumentId, sourceVersionId, content, "[]", KnowledgeRiskLevel.LOW,
            GradeEvidenceQuality.HIGH, GradeRecommendationStrength.STRONG
        );
    }

    private KnowledgeActionRequest actionRequest(String tenantId) {
        return new KnowledgeActionRequest(
            "req-1", "trace-1", tenantId, null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.review"), "pkg-2026.06", "提交审核"
        );
    }

    private KnowledgeCandidateReviewRequest candidateReviewRequest(String tenantId) {
        return new KnowledgeCandidateReviewRequest(
            "req-1", "trace-1", tenantId, null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.review"), "pkg-2026.06",
            KnowledgeCandidateReviewDecision.APPROVE, "同意"
        );
    }

    private CandidateClassification classification(Long id, Long identityId, Long candidateVersionId,
                                                   Long activeVersionId, CandidateClassificationType type,
                                                   CandidateReviewStatus status) {
        Instant now = Instant.now();
        return new CandidateClassification(
            id, "t-1", "tenant:t-1", identityId, candidateVersionId, activeVersionId, type, status,
            sha256("candidate-" + candidateVersionId), "测试分类依据", "当前 ACTIVE 与候选对照",
            now, "init", now, "init"
        );
    }

    private SourceDocument sourceDocument(Long id, SourceAuthorityLevel authorityLevel) {
        return sourceDocument("t-1", id, authorityLevel);
    }

    private SourceDocument sourceDocument(String tenantId, Long id, SourceAuthorityLevel authorityLevel) {
        Instant now = Instant.now();
        return new SourceDocument(
            id, tenantId, "SRC." + id, SourceType.GUIDELINE, authorityLevel,
            "来源分级依据", "来源文件", "发布机构", "LICENSE", "zh-CN",
            now, "tester", now, "tester"
        );
    }

    private String sha256(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unused")
    private List<KnowledgeAssetVersion> nothing() {
        return List.of();
    }
}
