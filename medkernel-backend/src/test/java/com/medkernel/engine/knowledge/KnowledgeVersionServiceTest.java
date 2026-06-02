package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
    private KnowledgeVersionService service;

    @BeforeEach
    void setUp() {
        identityRepo = Mockito.mock(KnowledgeIdentityRepository.class);
        versionRepo = Mockito.mock(KnowledgeAssetVersionRepository.class);
        supersessionRepo = Mockito.mock(KnowledgeSupersessionRepository.class);
        citationRepo = Mockito.mock(CitationRepository.class);
        sourceDocRepo = Mockito.mock(SourceDocumentRepository.class);
        projectionRefreshPort = Mockito.mock(KnowledgeProjectionRefreshPort.class);
        service = new KnowledgeVersionService(
            identityRepo, versionRepo, supersessionRepo, citationRepo, sourceDocRepo, projectionRefreshPort);
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-99"));

        // 默认 save 返回参数，方便断言保留字段
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(identityRepo.save(any(KnowledgeIdentity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supersessionRepo.save(any(KnowledgeSupersession.class))).thenAnswer(inv -> inv.getArgument(0));
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
    void createDraftVersionWithStandardRequestUsesPathIdentity() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(sourceDocument(7L, SourceAuthorityLevel.B_GUIDELINE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L)).thenReturn(List.of());

        KnowledgeAssetVersion created = service.createDraftVersion(1L, versionCreateRequest("v2"));

        assertThat(created.identityId()).isEqualTo(1L);
        assertThat(created.versionNo()).isEqualTo("v2");
        assertThat(created.status()).isEqualTo(KnowledgeVersionStatus.UNDER_REVIEW);
        assertThat(created.contentHash()).isNotBlank();
        assertThat(created.authorityLevel()).isEqualTo(SourceAuthorityLevel.B_GUIDELINE);
        assertThat(created.gradeQuality()).isEqualTo(GradeEvidenceQuality.HIGH);
        assertThat(created.gradeStrength()).isEqualTo(GradeRecommendationStrength.STRONG);
        assertThat(created.createdBy()).isEqualTo("u-99");
    }

    @Test
    void createDraftVersionStoresCanonicalSha256() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(sourceDocument(10L, SourceAuthorityLevel.C_CONSENSUS_LITERATURE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L)).thenReturn(List.of());

        KnowledgeAssetVersion created = service.createDraftVersion(draftRequest(1L, "v2", "真实指南内容"));

        assertThat(created.contentHash()).isEqualTo(sha256("真实指南内容"));
        assertThat(created.contentHash()).matches("[0-9a-f]{64}");
        assertThat(created.authorityLevel()).isEqualTo(SourceAuthorityLevel.C_CONSENSUS_LITERATURE);
        assertThat(created.gradeQuality()).isEqualTo(GradeEvidenceQuality.MODERATE);
        assertThat(created.gradeStrength()).isEqualTo(GradeRecommendationStrength.WEAK);
    }

    @Test
    void createDraftVersionRejectsBlankContentInsteadOfHashingEmptyString() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(sourceDocument(10L, SourceAuthorityLevel.C_CONSENSUS_LITERATURE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createDraftVersion(draftRequest(1L, "v2", "   ")))
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
    void listCandidatesReturnsKnow02PendingEmptyContract() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));

        KnowledgeCandidateResponse response = service.listCandidates(1L);

        assertThat(response.identityId()).isEqualTo(1L);
        assertThat(response.available()).isFalse();
        assertThat(response.reasonCode()).isEqualTo("KNOW_02_PENDING");
        assertThat(response.candidates()).isEmpty();
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
    void createDraftVersionInCustomerTenantDoesNotWriteBackToPlatformTenant() {
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

        KnowledgeAssetVersion saved =
            service.createDraftVersion(draftRequest(1L, "hospital-v1", "医院本地定制内容"));

        assertThat(saved.tenantId()).isEqualTo("t-hospital");
        verify(identityRepo, never()).findByTenantIdAndId("t-1", 1L);
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L);
    }

    // ─── helpers ──────────────────────────────────────────────

    private KnowledgeIdentity identity(Long id, Long currentVersionId) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            id, "t-1", "DRUG.X", KnowledgeDomain.DRUG, "测试主题", null, null,
            KnowledgeIdentityStatus.ACTIVE, currentVersionId,
            now, "init", now, "init"
        );
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, KnowledgeVersionStatus status, KnowledgeRiskLevel risk) {
        return version(id, identityId, status, risk, SourceAuthorityLevel.B_GUIDELINE);
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, KnowledgeVersionStatus status,
                                          KnowledgeRiskLevel risk, SourceAuthorityLevel authorityLevel) {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(
            id, "t-1", identityId, "v1", "label",
            null, null, sha256("知识版本夹具内容-" + id), null,
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
        return new KnowledgeVersionCreateRequest(
            "req-1", "trace-1", "t-1", null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.write"), "pkg-2026.06",
            versionNo, "2026 版", 7L, 8L, "真实指南内容", "[]", KnowledgeRiskLevel.LOW,
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

    private DraftVersionCreateRequest draftRequest(Long identityId, String versionNo, String content) {
        return new DraftVersionCreateRequest(
            identityId, versionNo, "医院定制版本", 10L, 20L, content, null, KnowledgeRiskLevel.MEDIUM,
            GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.WEAK);
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
