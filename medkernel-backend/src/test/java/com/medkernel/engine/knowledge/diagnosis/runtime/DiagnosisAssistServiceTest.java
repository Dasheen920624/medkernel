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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidence;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidenceEvaluator;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidencePolicy;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidencePolicyRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCarePointer;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCarePointerRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCarePointerType;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCareTargetType;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCriterion;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCriterionRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisDifferential;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisDifferentialRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisDirection;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisMatcher;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisWeight;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.BusinessMetrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 鉴别诊断编排：命中产候选 + 高危/证据排序 + 空态非排除 + 落库治理（trigger DIAGNOSIS 卡 / 空态不落库）。 */
class DiagnosisAssistServiceTest {

    private ContextSnapshotService snapshots;
    private RuntimeReleaseDiagnosisSelector runtimeDiagnoses;
    private DiagnosisCriterionRepository criteria;
    private DiagnosisCarePointerRepository carePointers;
    private DiagnosisDifferentialRepository differentials;
    private KnowledgeIdentityRepository identities;
    private DiagnosisConfidencePolicyRepository policies;
    private DiagnosisFindingExtractor extractor;
    private DiagnosisRedlinePort redlinePort;
    private RecommendationEngineService recommendationEngine;
    private BusinessMetrics businessMetrics;
    private DiagnosisAssistService service;

    private final DiagnosisConfidencePolicy policy = new DiagnosisConfidencePolicy(
        1L, "t-1", "DEFAULT", 2, true, 1, null, "u", null, "u", null);

    @BeforeEach
    void setUp() {
        snapshots = mock(ContextSnapshotService.class);
        runtimeDiagnoses = mock(RuntimeReleaseDiagnosisSelector.class);
        criteria = mock(DiagnosisCriterionRepository.class);
        carePointers = mock(DiagnosisCarePointerRepository.class);
        differentials = mock(DiagnosisDifferentialRepository.class);
        identities = mock(KnowledgeIdentityRepository.class);
        policies = mock(DiagnosisConfidencePolicyRepository.class);
        extractor = mock(DiagnosisFindingExtractor.class);
        redlinePort = mock(DiagnosisRedlinePort.class);
        recommendationEngine = mock(RecommendationEngineService.class);
        businessMetrics = mock(BusinessMetrics.class);
        DiagnosisMatcher matcher = new DiagnosisMatcher(new DiagnosisConfidenceEvaluator());
        service = new DiagnosisAssistService(snapshots, runtimeDiagnoses, criteria, carePointers,
            differentials, identities, policies,
            matcher, extractor, redlinePort, recommendationEngine, new ObjectMapper(), businessMetrics);

        when(snapshots.findById(any())).thenReturn(new ContextSnapshotResponse(
            "snap-1", null, null, "runtime-release-test", null, List.of(), Map.of(), Instant.now(), "trace-dx"));
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
        when(carePointers.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).thenReturn(List.of(
            carePointer(DiagnosisCarePointerType.WORKUP, DiagnosisCareTargetType.RULE, "RULE.LAB.REVIEW"),
            carePointer(DiagnosisCarePointerType.PATHWAY, DiagnosisCareTargetType.PATHWAY, "PATH.RESP")
        ));

        DiagnosisAssistResponse response = service.assist(new DiagnosisAssistRequest("snap-1"));

        assertThat(response.candidates()).singleElement().satisfies(candidate ->
            assertThat(candidate.careSuggestions())
                .extracting(DiagnosisCareSuggestion::targetRef)
                .containsExactly("RULE.LAB.REVIEW", "PATH.RESP"));
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
            assertThat(card.sources().getFirst().sourceRefId()).isEqualTo("DX.PNEU");
            assertThat(card.sources().getFirst().citationLocator()).isEqualTo("knowledge_version:t-1:10");
            assertThat(card.suggestedAction()).contains("2 项诊疗建议");
            assertThat(card.explanationJson()).contains("RULE.LAB.REVIEW", "PATH.RESP");
        });
    }

    @Test
    void exposesDifferentialDiagnosisKeyPointsInResponseAndRecommendationExplanation() {
        stubStrongHit();
        when(differentials.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).thenReturn(List.of(
            differential(99L, "发热伴咳嗽需与肺结核鉴别", "胸片、痰涂片或结核感染 T 细胞检测")
        ));
        when(identities.findByTenantIdAndId("t-1", 99L))
            .thenReturn(Optional.of(identity(99L, "DX.TB", "肺结核")));

        DiagnosisAssistResponse response = service.assist(new DiagnosisAssistRequest("snap-1"));

        assertThat(response.candidates()).singleElement().satisfies(candidate ->
            assertThat(candidate.differentials()).singleElement().satisfies(differential -> {
                assertThat(differential.differentialIdentityId()).isEqualTo(99L);
                assertThat(differential.identityCode()).isEqualTo("DX.TB");
                assertThat(differential.diagnosisName()).isEqualTo("肺结核");
                assertThat(differential.keyPoint()).contains("肺结核鉴别");
                assertThat(differential.suggestedWorkup()).contains("胸片");
            }));
        ArgumentCaptor<RecommendationTriggerRequest> cap = ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(recommendationEngine).trigger(cap.capture());
        assertThat(cap.getValue().candidateCards()).singleElement().satisfies(card ->
            assertThat(card.explanationJson()).contains("肺结核", "肺结核鉴别", "胸片"));
    }

    @Test
    void ranksStrongBeforeModerate() {
        when(extractor.extract(eq("t-1"), eq("runtime-release-test"), any()))
            .thenReturn(new ExtractedFindings(Set.of("FEVER", "COUGH", "RASH"), List.of()));
        when(runtimeDiagnoses.select("t-1", "runtime-release-test")).thenReturn(List.of(
            runtimeDiagnosis(20L, 200L, "DX.MOD", "中候选", SourceAuthorityLevel.A_REGULATION),   // 故意先放 MODERATE
            runtimeDiagnosis(10L, 100L, "DX.STRONG", "强候选", SourceAuthorityLevel.B_GUIDELINE)));  // STRONG
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).thenReturn(List.of(
            crit(10L, "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(10L, "COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR)));
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 20L)).thenReturn(List.of(
            crit(20L, "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(20L, "RASH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MINOR)));

        DiagnosisAssistResponse response = service.assist(new DiagnosisAssistRequest("snap-1"));

        assertThat(response.candidates()).extracting(DiagnosisCandidate::confidence)
            .containsExactly(DiagnosisConfidence.STRONG, DiagnosisConfidence.MODERATE);
    }

    @Test
    void emptyStateIsAdvisoryNotExclusionAndDoesNotPersist() {
        when(extractor.extract(eq("t-1"), eq("runtime-release-test"), any()))
            .thenReturn(new ExtractedFindings(Set.of("FEVER"), List.of()));
        when(runtimeDiagnoses.select("t-1", "runtime-release-test")).thenReturn(List.of()); // 机构生效版本未启用诊断版本

        DiagnosisAssistResponse response = service.assist(new DiagnosisAssistRequest("snap-1"));

        assertThat(response.candidates()).isEmpty();
        assertThat(response.advisoryNote()).isEqualTo(DiagnosisAssistService.ADVISORY_EMPTY);
        assertThat(response.advisoryNote()).contains("不是排除诊断");
        verify(recommendationEngine, never()).trigger(any()); // 空态不落库
        verify(businessMetrics).incDiagnosisAssist(); // 调用数即便空态也计
        verify(businessMetrics, never()).incDiagnosisCandidate(any()); // 无候选不计分级分布
    }

    @Test
    void activeDiagnosisVersionOutsideRuntimeReleaseDoesNotParticipate() {
        // 即便知识版本本身 ACTIVE，只要未被当前机构生效版本启用，就不能进入临床辅助诊疗。
        when(extractor.extract(eq("t-1"), eq("runtime-release-test"), any()))
            .thenReturn(new ExtractedFindings(Set.of("FEVER", "COUGH"), List.of()));
        when(runtimeDiagnoses.select("t-1", "runtime-release-test")).thenReturn(List.of());
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).thenReturn(List.of(
            crit(10L, "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(10L, "COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR)));

        DiagnosisAssistResponse response = service.assist(new DiagnosisAssistRequest("snap-1"));

        assertThat(response.candidates()).isEmpty();
        assertThat(response.advisoryNote()).contains("不是排除诊断");
        verify(recommendationEngine, never()).trigger(any());
    }

    @Test
    void emitsObservabilityMetricsForInvocationAndCandidateDistribution() {
        stubStrongHit();

        service.assist(new DiagnosisAssistRequest("snap-1"));

        verify(businessMetrics).incDiagnosisAssist();
        verify(businessMetrics).incDiagnosisCandidate("STRONG"); // 候选按置信等级计入分级分布
    }

    @Test
    void redlinePinnedCandidateSortsAboveStrongerEvidenceAndCardRiskIsHigh() {
        // STRONG 肺炎 vs 仅 MODERATE 但被红线置顶的夹层 → 夹层排第一、redline=true、卡风险 HIGH（高危先行压过证据充分）
        when(extractor.extract(eq("t-1"), eq("runtime-release-test"), any()))
            .thenReturn(new ExtractedFindings(Set.of("FEVER", "COUGH", "TEARING_PAIN"), List.of()));
        when(runtimeDiagnoses.select("t-1", "runtime-release-test")).thenReturn(List.of(
            runtimeDiagnosis(10L, 100L, "DX.PNEU", "肺炎", SourceAuthorityLevel.B_GUIDELINE),    // STRONG 肺炎
            runtimeDiagnosis(20L, 200L, "DX.AORTIC", "主动脉夹层", SourceAuthorityLevel.A_REGULATION))); // MODERATE 夹层（红线置顶）
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).thenReturn(List.of(
            crit(10L, "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(10L, "COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR)));
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 20L)).thenReturn(List.of(
            crit(20L, "TEARING_PAIN", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(20L, "FEVER", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MINOR)));
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
        when(extractor.extract(eq("t-1"), eq("runtime-release-test"), any()))
            .thenReturn(new ExtractedFindings(Set.of("FEVER", "COUGH"), List.of("LOCALX")));
        when(runtimeDiagnoses.select("t-1", "runtime-release-test"))
            .thenReturn(List.of(runtimeDiagnosis(10L, 100L, "DX.PNEU", "社区获得性肺炎", SourceAuthorityLevel.A_REGULATION)));
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).thenReturn(List.of(
            crit(10L, "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit(10L, "COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR)));
    }

    private DiagnosisCriterion crit(Long versionId, String code, DiagnosisDirection dir, DiagnosisWeight w) {
        Instant now = Instant.now();
        return new DiagnosisCriterion(null, "t-1", versionId, code, dir, w, null, null, null,
            now, "u", now, "u", "tr");
    }

    private DiagnosisCarePointer carePointer(
            DiagnosisCarePointerType pointerType,
            DiagnosisCareTargetType targetType,
            String targetRef) {
        Instant now = Instant.now();
        return new DiagnosisCarePointer(null, "t-1", 10L, pointerType, targetType, targetRef, true,
            "医师确认诊断后评估", now, "u", now, "u", "tr");
    }

    private DiagnosisDifferential differential(Long identityId, String keyPoint, String suggestedWorkup) {
        Instant now = Instant.now();
        return new DiagnosisDifferential(
            null, "t-1", 10L, identityId, keyPoint, suggestedWorkup,
            now, "u", now, "u", "tr");
    }

    private KnowledgeIdentity identity(Long id, String code, String subject) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(id, "t-1", code, KnowledgeDomain.DIAGNOSIS, subject, null, null,
            KnowledgeIdentityStatus.ACTIVE, null, now, "system", now, "system");
    }

    private RuntimeDiagnosisReference runtimeDiagnosis(
            Long versionId,
            Long identityId,
            String identityCode,
            String diagnosisName,
            SourceAuthorityLevel authority) {
        return new RuntimeDiagnosisReference(
            "t-1",
            identityId,
            identityCode,
            diagnosisName,
            versionId,
            "v1.0",
            authority.name()
        );
    }
}
