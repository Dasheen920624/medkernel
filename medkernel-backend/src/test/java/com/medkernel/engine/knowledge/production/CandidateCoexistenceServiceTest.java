package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.CandidateClassification;
import com.medkernel.engine.knowledge.CandidateClassificationRepository;
import com.medkernel.engine.knowledge.CandidateClassificationType;
import com.medkernel.engine.knowledge.CandidateReviewStatus;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 候选共存读模型测试（AIK-STD-09/11）。
 *
 * <p>锁定审核前候选只读共存：待审候选不可执行，现行 ACTIVE 仍是唯一执行版本；审核通过才进入 SYS-08 原子替换。
 */
class CandidateCoexistenceServiceTest {

    private static final String TENANT = "tenant-coexist";

    private KnowledgeAssetVersionRepository versionRepository;
    private CandidateClassificationRepository classificationRepository;
    private KnowledgeProductionCandidateRepository candidateRepository;
    private KnowledgeProductionJobRepository jobRepository;
    private CandidateCoexistenceService service;

    @BeforeEach
    void setUp() {
        versionRepository = mock(KnowledgeAssetVersionRepository.class);
        classificationRepository = mock(CandidateClassificationRepository.class);
        candidateRepository = mock(KnowledgeProductionCandidateRepository.class);
        jobRepository = mock(KnowledgeProductionJobRepository.class);
        service = new CandidateCoexistenceService(
            versionRepository, classificationRepository, candidateRepository, jobRepository);
        RequestContext.restore(new RequestContext.Snapshot("trace-coexist", OrgScope.tenant(TENANT), "u"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void pendingCandidateShowsActiveVersionAndBlocksCandidateExecution() {
        KnowledgeAssetVersion active = version(10L, 1L, "v1", KnowledgeVersionStatus.ACTIVE);
        KnowledgeAssetVersion candidate = version(22L, 1L, "v2", KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
        CandidateClassification classification = classification(88L, candidate.id(), active.id(),
            CandidateClassificationType.SAME_IDENTITY_NEW_VERSION);
        KnowledgeProductionCandidate lineage = productionCandidate("kv:1:v2", "job-coexist");
        KnowledgeProductionJob job = productionJob("job-coexist", KnowledgeProducer.API_MODEL);
        when(versionRepository.findByTenantIdAndIdentityIdAndVersionNo(TENANT, 1L, "v2"))
            .thenReturn(Optional.of(candidate));
        when(versionRepository.findActiveByEffectiveScope(TENANT, 1L, "tenant:" + TENANT,
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(active));
        when(classificationRepository.findByTenantIdAndCandidateVersionId(TENANT, 22L))
            .thenReturn(Optional.of(classification));
        when(candidateRepository.findByTenantIdAndCandidateRefIn(TENANT, List.of("kv:1:v2")))
            .thenReturn(List.of(lineage));
        when(jobRepository.findByTenantIdAndJobCode(TENANT, "job-coexist")).thenReturn(Optional.of(job));

        CandidateCoexistenceView view = service.resolve("kv:1:v2");

        assertThat(view.candidateExecutable()).isFalse();
        assertThat(view.activeExecutable()).isTrue();
        assertThat(view.candidateVersion().status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
        assertThat(view.activeVersion().status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);
        assertThat(view.reviewStatus()).isEqualTo(CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        assertThat(view.approvalOutcome()).isEqualTo("APPROVE_REPLACE_ACTIVE");
        assertThat(view.replacementReminder()).contains("SYS-08 原子替换").contains("审核前仍由现行 ACTIVE=v1 执行");
        assertThat(view.safetyNotice()).contains("不参与临床执行");
        assertThat(view.productionLineage()).isNotNull();
        assertThat(view.productionLineage().producer()).isEqualTo(KnowledgeProducer.API_MODEL);
    }

    @Test
    void newIdentityCandidateStillBlocksExecutionBeforeFirstActivation() {
        KnowledgeAssetVersion candidate = version(33L, 2L, "v1", KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
        CandidateClassification classification = classification(90L, candidate.id(), null,
            CandidateClassificationType.NEW_ASSET);
        when(versionRepository.findByTenantIdAndIdentityIdAndVersionNo(TENANT, 2L, "v1"))
            .thenReturn(Optional.of(candidate));
        when(versionRepository.findActiveByEffectiveScope(TENANT, 2L, "tenant:" + TENANT,
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.empty());
        when(classificationRepository.findByTenantIdAndCandidateVersionId(TENANT, 33L))
            .thenReturn(Optional.of(classification));
        when(candidateRepository.findByTenantIdAndCandidateRefIn(TENANT, List.of("kv:2:v1")))
            .thenReturn(List.of());

        CandidateCoexistenceView view = service.resolve("kv:2:v1");

        assertThat(view.activeVersion()).isNull();
        assertThat(view.activeExecutable()).isFalse();
        assertThat(view.candidateExecutable()).isFalse();
        assertThat(view.classification()).isEqualTo(CandidateClassificationType.NEW_ASSET);
        assertThat(view.approvalOutcome()).isEqualTo("APPROVE_ACTIVATE_FIRST_VERSION");
        assertThat(view.replacementReminder()).contains("首次激活").contains("候选 v1 不得直接用于临床执行");
        assertThat(view.productionLineage()).isNull();
    }

    @Test
    void nonPendingCandidateCannotBePresentedAsCoexistence() {
        KnowledgeAssetVersion active = version(10L, 1L, "v1", KnowledgeVersionStatus.ACTIVE);
        when(versionRepository.findByTenantIdAndIdentityIdAndVersionNo(TENANT, 1L, "v1"))
            .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.resolve("kv:1:v1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(versionRepository, never()).findActiveByEffectiveScope(any(), any(), any(), any());
    }

    @Test
    void invalidCandidateRefIsRejectedBeforeRepositoryLookup() {
        assertThatThrownBy(() -> service.resolve("bad-ref"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(versionRepository, never()).findByTenantIdAndIdentityIdAndVersionNo(any(), any(), any());
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, String versionNo, KnowledgeVersionStatus status) {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(
            id,
            TENANT,
            identityId,
            versionNo,
            "版本 " + versionNo,
            7L,
            8L,
            "hash-" + id,
            "anchor",
            status,
            KnowledgeRiskLevel.MEDIUM,
            SourceAuthorityLevel.B_GUIDELINE,
            GradeEvidenceQuality.MODERATE,
            GradeRecommendationStrength.STRONG,
            null,
            "tenant:" + TENANT,
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            status == KnowledgeVersionStatus.ACTIVE ? "active:" + id : "pending:" + id,
            status == KnowledgeVersionStatus.ACTIVE ? now : null,
            null,
            status == KnowledgeVersionStatus.ACTIVE ? "reviewer" : null,
            status == KnowledgeVersionStatus.ACTIVE ? now : null,
            status == KnowledgeVersionStatus.ACTIVE ? now : null,
            null,
            null,
            null,
            now,
            "u",
            now,
            "u",
            12,
            null);
    }

    private CandidateClassification classification(Long id, Long candidateVersionId, Long activeVersionId,
                                                   CandidateClassificationType type) {
        Instant now = Instant.now();
        return new CandidateClassification(
            id,
            TENANT,
            "/",
            candidateVersionId == 33L ? 2L : 1L,
            candidateVersionId,
            activeVersionId,
            type,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW,
            "hash-" + candidateVersionId,
            "basis",
            "当前 ACTIVE 与候选对照：审核前候选不执行",
            now,
            "u",
            now,
            "u");
    }

    private KnowledgeProductionCandidate productionCandidate(String candidateRef, String jobCode) {
        return new KnowledgeProductionCandidate(
            1L,
            TENANT,
            jobCode,
            "identity",
            "0".repeat(64),
            candidateRef,
            KnowledgeRiskLevel.MEDIUM,
            Instant.now(),
            "u");
    }

    private KnowledgeProductionJob productionJob(String jobCode, KnowledgeProducer producer) {
        Instant now = Instant.now();
        return new KnowledgeProductionJob(
            1L,
            TENANT,
            jobCode,
            "run",
            VersionedAssetType.KNOWLEDGE,
            producer,
            TargetPipeline.TENANT_OVERLAY,
            KnowledgeDomain.GENERAL,
            "strategy",
            ProductionJobStatus.RUNNING,
            1,
            "{}",
            now,
            "u",
            now,
            "u",
            "trace");
    }
}
