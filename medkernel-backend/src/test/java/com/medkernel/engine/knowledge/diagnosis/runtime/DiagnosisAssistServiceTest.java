package com.medkernel.engine.knowledge.diagnosis.runtime;

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
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidence;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidenceEvaluator;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidencePolicy;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidencePolicyRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCriterion;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCriterionRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisDirection;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisMatcher;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisWeight;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 鉴别诊断编排：命中产候选 + 高危/证据排序 + 空态非排除 + 落库治理（trigger DIAGNOSIS 卡 / 空态不落库）。 */
class DiagnosisAssistServiceTest {

    private ContextSnapshotService snapshots;
    private KnowledgeAssetVersionRepository versions;
    private KnowledgeIdentityRepository identities;
    private DiagnosisCriterionRepository criteria;
    private DiagnosisConfidencePolicyRepository policies;
    private DiagnosisFindingExtractor extractor;
    private DiagnosisRedlinePort redlinePort;
    private RecommendationEngineService recommendationEngine;
    private DiagnosisAssistService service;

    private final DiagnosisConfidencePolicy policy = new DiagnosisConfidencePolicy(
        1L, "t-1", "DEFAULT", 2, true, 1, null, "u", null, "u", null);

    @BeforeEach
    void setUp() {
        snapshots = mock(ContextSnapshotService.class);
        versions = mock(KnowledgeAssetVersionRepository.class);
        identities = mock(KnowledgeIdentityRepository.class);
        criteria = mock(DiagnosisCriterionRepository.class);
        policies = mock(DiagnosisConfidencePolicyRepository.class);
        extractor = mock(DiagnosisFindingExtractor.class);
        redlinePort = mock(DiagnosisRedlinePort.class);
        recommendationEngine = mock(RecommendationEngineService.class);
        DiagnosisMatcher matcher = new DiagnosisMatcher(new DiagnosisConfidenceEvaluator());
        service = new DiagnosisAssistService(snapshots, versions, identities, criteria, policies,
            matcher, extractor, redlinePort, recommendationEngine, new ObjectMapper());

        when(snapshots.findById(any())).thenReturn(new ContextSnapshotResponse(
            "snap-1", null, null, null, null, Instant.now(), "trace-dx"));
        when(policies.findByTenantIdAndScopeKey("t-1", "DEFAULT")).thenReturn(Optional.of(policy));
        when(redlinePort.pinnedDiagnosisCodes(any(), any())).thenReturn(Set.of());

        RequestContext.restore(new RequestContext.Snapshot("trace-dx", OrgScope.tenant("t-1"), "doctor-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void hitProducesExplainableStrongCandidate() {
        stubStrongHit();

        DiagnosisAssistResponse response = service.assist(new DiagnosisAssistRequest("snap-1"));

        assertThat(response.candidates()).singleElement().satisfies(c -> {
            assertThat(c.confidence()).isEqualTo(DiagnosisConfidence.STRONG);
            assertThat(c.diagnosisName()).isEqualTo("社区获得性肺炎");
            assertThat(c.icdCode()).isEqualTo("DX.PNEU");
            assertThat(c.sourceVersionId()).isEqualTo(10L);
            assertThat(c.supporting()).contains("FEVER", "COUGH");
            assertThat(c.redline()).isFalse();
        });
        assertThat(response.unmappedFindings()).containsExactly("LOCALX");
        assertThat(response.advisoryNote()).isNotBlank();
    }

    @Test
    void persistsDiagnosisCardsViaRecommendationTrigger() {
        stubStrongHit();

        service.assist(new DiagnosisAssistRequest("snap-1"));

        ArgumentCaptor<RecommendationTriggerRequest> cap = ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(recommendationEngine).trigger(cap.capture());
        RecommendationTriggerRequest req = cap.getValue();
        assertThat(req.triggerType()).isEqualTo("patient-view"); // 合法 CDS Hook
        assertThat(req.scenarioCode()).isEqualTo("S16");
        assertThat(req.candidateCards()).singleElement().satisfies(card -> {
            assertThat(card.cardType()).isEqualTo(RecommendationCardType.DIAGNOSIS);
            assertThat(card.requiresPhysicianConfirmation()).isTrue();
            assertThat(card.aiGenerated()).isFalse();
            assertThat(card.sources()).hasSize(1);
        });
    }

    @Test
    void ranksStrongBeforeModerate() {
        when(extractor.extract(eq("t-1"), any()))
            .thenReturn(new ExtractedFindings(Set.of("FEVER", "COUGH", "RASH"), List.of()));
        when(versions.findActiveDiagnosisVersions("t-1")).thenReturn(List.of(
            version(20L, 200L, SourceAuthorityLevel.A_REGULATION),   // 故意先放 MODERATE
            version(10L, 100L, SourceAuthorityLevel.B_GUIDELINE)));  // STRONG
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).thenReturn(List.of(
            crit(10L, "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(10L, "COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR)));
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 20L)).thenReturn(List.of(
            crit(20L, "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(20L, "RASH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MINOR)));
        when(identities.findByTenantIdAndId("t-1", 100L)).thenReturn(Optional.of(identity(100L, "DX.STRONG", "强候选")));
        when(identities.findByTenantIdAndId("t-1", 200L)).thenReturn(Optional.of(identity(200L, "DX.MOD", "中候选")));

        DiagnosisAssistResponse response = service.assist(new DiagnosisAssistRequest("snap-1"));

        assertThat(response.candidates()).extracting(DiagnosisCandidate::confidence)
            .containsExactly(DiagnosisConfidence.STRONG, DiagnosisConfidence.MODERATE);
    }

    @Test
    void emptyStateIsAdvisoryNotExclusionAndDoesNotPersist() {
        when(extractor.extract(eq("t-1"), any()))
            .thenReturn(new ExtractedFindings(Set.of("FEVER"), List.of()));
        when(versions.findActiveDiagnosisVersions("t-1")).thenReturn(List.of()); // 无 ACTIVE 诊断版本

        DiagnosisAssistResponse response = service.assist(new DiagnosisAssistRequest("snap-1"));

        assertThat(response.candidates()).isEmpty();
        assertThat(response.advisoryNote()).isEqualTo(DiagnosisAssistService.ADVISORY_EMPTY);
        assertThat(response.advisoryNote()).contains("不是排除诊断");
        verify(recommendationEngine, never()).trigger(any()); // 空态不落库
    }

    @Test
    void redlinePinnedCandidateSortsAboveStrongerEvidenceAndCardRiskIsHigh() {
        // STRONG 肺炎 vs 仅 MODERATE 但被红线置顶的夹层 → 夹层排第一、redline=true、卡风险 HIGH（高危先行压过证据充分）
        when(extractor.extract(eq("t-1"), any()))
            .thenReturn(new ExtractedFindings(Set.of("FEVER", "COUGH", "TEARING_PAIN"), List.of()));
        when(versions.findActiveDiagnosisVersions("t-1")).thenReturn(List.of(
            version(10L, 100L, SourceAuthorityLevel.B_GUIDELINE),    // STRONG 肺炎
            version(20L, 200L, SourceAuthorityLevel.A_REGULATION))); // MODERATE 夹层（红线置顶）
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).thenReturn(List.of(
            crit(10L, "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(10L, "COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR)));
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 20L)).thenReturn(List.of(
            crit(20L, "TEARING_PAIN", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(20L, "FEVER", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MINOR)));
        when(identities.findByTenantIdAndId("t-1", 100L)).thenReturn(Optional.of(identity(100L, "DX.PNEU", "肺炎")));
        when(identities.findByTenantIdAndId("t-1", 200L))
            .thenReturn(Optional.of(identity(200L, "DX.AORTIC", "主动脉夹层")));
        when(redlinePort.pinnedDiagnosisCodes(eq("t-1"), any())).thenReturn(Set.of("DX.AORTIC"));

        DiagnosisAssistResponse response = service.assist(new DiagnosisAssistRequest("snap-1"));

        assertThat(response.candidates()).first().satisfies(c -> {
            assertThat(c.icdCode()).isEqualTo("DX.AORTIC");
            assertThat(c.confidence()).isEqualTo(DiagnosisConfidence.MODERATE);
            assertThat(c.redline()).isTrue();
        });
        ArgumentCaptor<RecommendationTriggerRequest> cap = ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(recommendationEngine).trigger(cap.capture());
        assertThat(cap.getValue().candidateCards())
            .filteredOn(card -> "dx-200".equals(card.cardCode()))
            .singleElement()
            .satisfies(card -> assertThat(card.riskLevel()).isEqualTo(RecommendationRiskLevel.HIGH));
    }

    private void stubStrongHit() {
        when(extractor.extract(eq("t-1"), any()))
            .thenReturn(new ExtractedFindings(Set.of("FEVER", "COUGH"), List.of("LOCALX")));
        when(versions.findActiveDiagnosisVersions("t-1"))
            .thenReturn(List.of(version(10L, 100L, SourceAuthorityLevel.A_REGULATION)));
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).thenReturn(List.of(
            crit(10L, "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(10L, "COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR)));
        when(identities.findByTenantIdAndId("t-1", 100L))
            .thenReturn(Optional.of(identity(100L, "DX.PNEU", "社区获得性肺炎")));
    }

    private DiagnosisCriterion crit(Long versionId, String code, DiagnosisDirection dir, DiagnosisWeight w) {
        Instant now = Instant.now();
        return new DiagnosisCriterion(null, "t-1", versionId, code, dir, w, null, null, null,
            now, "u", now, "u", "tr");
    }

    private KnowledgeIdentity identity(Long id, String code, String subject) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(id, "t-1", code, KnowledgeDomain.DIAGNOSIS, subject, null, null,
            KnowledgeIdentityStatus.ACTIVE, null, now, "system", now, "system");
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, SourceAuthorityLevel authority) {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(id, "t-1", identityId, "v1.0", null, null, null, "h" + id, "[]",
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, authority,
            GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.WEAK, null,
            "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE, "scope-" + id,
            null, null, null, null, now, null, null, null, now, "system", now, "system");
    }
}
