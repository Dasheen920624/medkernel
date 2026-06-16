package com.medkernel.engine.knowledge.production.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.llm.eval.MedicalRegressionCase;
import com.medkernel.engine.llm.eval.MedicalRegressionCaseRepository;
import com.medkernel.engine.llm.eval.MedicalRegressionEvaluator;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/** AIK-STD-06 生成期影子评测服务单元测试。 */
class KnowledgeShadowEvaluationServiceTest {

    private final MedicalRegressionCaseRepository cases = mock(MedicalRegressionCaseRepository.class);
    private final KnowledgeShadowRunRepository runs = mock(KnowledgeShadowRunRepository.class);
    private final KnowledgeShadowEvaluationService service =
        new KnowledgeShadowEvaluationService(cases, new MedicalRegressionEvaluator(), runs);

    @BeforeEach
    void bindTenant() {
        RequestContext.restore(new RequestContext.Snapshot("trace-shadow", OrgScope.tenant("tenant-a"), "user-a"));
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void noBenchmarkRecordsNotReadyAndBlocksReview() {
        when(cases.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-a", "knowledge.production.rule", "Y")).thenReturn(List.of());

        KnowledgeShadowDecision decision = service.evaluate(candidate("血压≥140/90 诊断高血压。"),
            new KnowledgeShadowContext("tenant-a", "job-1", 10L, VersionedAssetType.RULE));

        assertThat(decision.readyForReview()).isFalse();
        assertThat(decision.status()).isEqualTo(KnowledgeShadowRunStatus.NOT_READY);
        KnowledgeShadowRun saved = savedRun();
        assertThat(saved.totalCases()).isZero();
        assertThat(saved.readyForReview()).isFalse();
        assertThat(saved.basis()).contains("未配置");
    }

    @Test
    void passingBenchmarkAllowsReviewAndRecordsMetrics() {
        when(cases.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-a", "knowledge.production.rule", "Y")).thenReturn(List.of(
            regressionCase("knowledge.production.rule", "血压≥140/90", null, "Y")));

        KnowledgeShadowDecision decision = service.evaluate(candidate("血压≥140/90 诊断高血压。"),
            new KnowledgeShadowContext("tenant-a", "job-1", 10L, VersionedAssetType.RULE));

        assertThat(decision.readyForReview()).isTrue();
        assertThat(decision.status()).isEqualTo(KnowledgeShadowRunStatus.PASSED);
        KnowledgeShadowRun saved = savedRun();
        assertThat(saved.totalCases()).isEqualTo(1);
        assertThat(saved.hitCount()).isEqualTo(1);
        assertThat(saved.missCount()).isZero();
        assertThat(saved.falsePositiveCount()).isZero();
        assertThat(saved.degradationDetected()).isFalse();
    }

    @Test
    void redlineBenchmarkPassedStillRequiresManualReviewButNotBlocked() {
        when(cases.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-a", "knowledge.production.rule", "Y")).thenReturn(List.of(
            regressionCase("knowledge.production.rule", "避免禁忌用药", "CONTRAINDICATION", "Y")));

        KnowledgeShadowDecision decision = service.evaluate(candidate("避免禁忌用药，并保留来源引用。"),
            new KnowledgeShadowContext("tenant-a", "job-1", 10L, VersionedAssetType.RULE));

        assertThat(decision.readyForReview()).isTrue();
        assertThat(decision.status()).isEqualTo(KnowledgeShadowRunStatus.PENDING_REVIEW);
        assertThat(savedRun().basis()).contains("高风险");
    }

    @Test
    void failedBenchmarkBlocksReviewAndMarksDegradation() {
        when(cases.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-a", "knowledge.production.rule", "Y")).thenReturn(List.of(
            regressionCase("knowledge.production.rule", "不存在短语", null, "N")));

        KnowledgeShadowDecision decision = service.evaluate(candidate("血压≥140/90 诊断高血压。"),
            new KnowledgeShadowContext("tenant-a", "job-1", 10L, VersionedAssetType.RULE));

        assertThat(decision.readyForReview()).isFalse();
        assertThat(decision.status()).isEqualTo(KnowledgeShadowRunStatus.FAILED);
        KnowledgeShadowRun saved = savedRun();
        assertThat(saved.missCount()).isEqualTo(1);
        assertThat(saved.degradationDetected()).isTrue();
        assertThat(saved.basis()).contains("未达标");
    }

    private KnowledgeShadowRun savedRun() {
        ArgumentCaptor<KnowledgeShadowRun> captor = ArgumentCaptor.forClass(KnowledgeShadowRun.class);
        org.mockito.Mockito.verify(runs).save(captor.capture());
        return captor.getValue();
    }

    private KnowledgeAssetEnvelope candidate(String payload) {
        return new KnowledgeAssetEnvelope(
            VersionedAssetType.RULE, "RULE-HTN", "高血压诊断规则", "draft-v1",
            List.of(new AssetSourceRef("SRC:v1:section-1", SourceAuthorityLevel.B_GUIDELINE)),
            SourceAuthorityLevel.B_GUIDELINE, GradeEvidenceQuality.MODERATE,
            GradeRecommendationStrength.STRONG, KnowledgeRiskLevel.MEDIUM, "tenant-a",
            "a".repeat(64), payload, AssetVersionStatus.DRAFT);
    }

    private MedicalRegressionCase regressionCase(String capabilityCode, String expected,
                                                 String redLineType, String citationRequired) {
        return new MedicalRegressionCase(null, "tenant-a", capabilityCode, "输入", expected,
            redLineType, citationRequired, "v1", "Y", Instant.EPOCH, "seed", Instant.EPOCH, "seed");
    }
}
