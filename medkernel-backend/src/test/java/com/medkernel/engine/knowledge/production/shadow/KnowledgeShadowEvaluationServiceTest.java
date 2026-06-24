package com.medkernel.engine.knowledge.production.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.production.generation.StrictB0TemplatePolicy;
import com.medkernel.engine.llm.eval.MedicalRegressionCase;
import com.medkernel.engine.llm.eval.MedicalRegressionCaseRepository;
import com.medkernel.engine.llm.eval.MedicalRegressionEvaluator;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/** AIK-STD-06 生成期影子评测服务单元测试。 */
class KnowledgeShadowEvaluationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MedicalRegressionCaseRepository cases = mock(MedicalRegressionCaseRepository.class);
    private final KnowledgeShadowRunRepository runs = mock(KnowledgeShadowRunRepository.class);
    private final KnowledgeShadowEvaluationService service =
        new KnowledgeShadowEvaluationService(
            cases, new MedicalRegressionEvaluator(), runs, new StrictB0TemplatePolicy(OBJECT_MAPPER));

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
    void strictB0NonModelTemplateSkipsModelBenchmarkAndEntersAuthoringReview() {
        KnowledgeShadowDecision decision = service.evaluate(candidate(VersionedAssetType.KNOWLEDGE, """
            {
              "generationMode": "B0_TEMPLATE",
              "medicalContentStatus": "PENDING_AUTHORING",
              "generatedByModel": false,
              "template": "KNOWLEDGE",
              "sections": {
                "scope": "待编著（结构：适用范围）",
                "content": "待编著（结构：知识正文）"
              },
              "sourceEvidence": [
                {
                  "anchorPath": "p46/§3/¶1",
                  "excerpt": "WHO 指南来源片段",
                  "contentHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                }
              ]
            }
            """), new KnowledgeShadowContext(
                "tenant-a", "job-b0", null, VersionedAssetType.KNOWLEDGE));

        assertThat(decision.readyForReview()).isTrue();
        assertThat(decision.status()).isEqualTo(KnowledgeShadowRunStatus.PENDING_REVIEW);
        KnowledgeShadowRun saved = savedRun();
        assertThat(saved.totalCases()).isZero();
        assertThat(saved.degradationDetected()).isFalse();
        assertThat(saved.basis()).contains("B0").contains("非模型").contains("人工编著审核");
        verifyNoInteractions(cases);
    }

    @Test
    void modelMarkedB0TemplateStillRequiresRealBenchmark() {
        when(cases.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-a", "knowledge.production.knowledge", "Y")).thenReturn(List.of());

        KnowledgeShadowDecision decision = service.evaluate(candidate(VersionedAssetType.KNOWLEDGE, """
            {
              "generationMode": "B0_TEMPLATE",
              "medicalContentStatus": "PENDING_AUTHORING",
              "generatedByModel": true,
              "template": "KNOWLEDGE",
              "sections": {
                "scope": "待编著（结构：适用范围）"
              },
              "sourceEvidence": [
                {
                  "anchorPath": "p46/§3/¶1",
                  "excerpt": "WHO 指南来源片段",
                  "contentHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                }
              ]
            }
            """), new KnowledgeShadowContext(
                "tenant-a", "job-model-b0", null, VersionedAssetType.KNOWLEDGE));

        assertThat(decision.readyForReview()).isFalse();
        assertThat(decision.status()).isEqualTo(KnowledgeShadowRunStatus.NOT_READY);
        assertThat(savedRun().basis()).contains("未配置真实影子评测基准集");
    }

    @Test
    void lowRiskModelSourceBoundaryEntersReviewWithoutReusingProviderRegressionPromptCases() {
        KnowledgeShadowDecision decision = service.evaluate(lowRiskSourceBoundaryModelCandidate(),
            new KnowledgeShadowContext("tenant-a", "job-source-boundary", null, VersionedAssetType.KNOWLEDGE));

        assertThat(decision.readyForReview()).isTrue();
        assertThat(decision.status()).isEqualTo(KnowledgeShadowRunStatus.PENDING_REVIEW);
        KnowledgeShadowRun saved = savedRun();
        assertThat(saved.totalCases()).isZero();
        assertThat(saved.degradationDetected()).isFalse();
        assertThat(saved.basis()).contains("低风险").contains("来源边界").contains("人工审核");
        verifyNoInteractions(cases);
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
    void redlineBenchmarkPassesWhenAllSafetyExpectationsAreMet() {
        when(cases.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-a", "knowledge.production.rule", "Y")).thenReturn(List.of(
            regressionCase("knowledge.production.rule", "避免禁忌用药", "CONTRAINDICATION", "Y")));

        KnowledgeShadowDecision decision = service.evaluate(candidate("避免禁忌用药，并保留来源引用。"),
            new KnowledgeShadowContext("tenant-a", "job-1", 10L, VersionedAssetType.RULE));

        assertThat(decision.readyForReview()).isTrue();
        assertThat(decision.status()).isEqualTo(KnowledgeShadowRunStatus.PASSED);
        assertThat(savedRun().basis()).contains("影子评测通过");
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

    @Test
    void citationFromDifferentSourceCannotPassShadowEvaluation() {
        when(cases.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-a", "knowledge.production.rule", "Y")).thenReturn(List.of(
            regressionCase("knowledge.production.rule", "血压≥140/90", null, "Y", "OTHER:v1:section-9")));

        KnowledgeShadowDecision decision = service.evaluate(candidate("血压≥140/90 诊断高血压。"),
            new KnowledgeShadowContext("tenant-a", "job-1", 10L, VersionedAssetType.RULE));

        assertThat(decision.readyForReview()).isFalse();
        assertThat(decision.status()).isEqualTo(KnowledgeShadowRunStatus.FAILED);
        assertThat(savedRun().basis()).contains("假引用=true");
    }

    private KnowledgeShadowRun savedRun() {
        ArgumentCaptor<KnowledgeShadowRun> captor = ArgumentCaptor.forClass(KnowledgeShadowRun.class);
        org.mockito.Mockito.verify(runs).save(captor.capture());
        return captor.getValue();
    }

    private KnowledgeAssetEnvelope candidate(String payload) {
        return candidate(VersionedAssetType.RULE, payload);
    }

    private KnowledgeAssetEnvelope candidate(VersionedAssetType assetType, String payload) {
        return new KnowledgeAssetEnvelope(
            assetType, assetType.name() + "-HTN", "高血压诊断规则", "draft-v1",
            List.of(new AssetSourceRef("SRC:v1:section-1", SourceAuthorityLevel.B_GUIDELINE)),
            SourceAuthorityLevel.B_GUIDELINE, GradeEvidenceQuality.MODERATE,
            GradeRecommendationStrength.STRONG, KnowledgeRiskLevel.MEDIUM, "tenant-a",
            "a".repeat(64), payload, AssetVersionStatus.DRAFT);
    }

    private KnowledgeAssetEnvelope lowRiskSourceBoundaryModelCandidate() {
        String payload = """
            {
              "aiGenerated": true,
              "modelTaskId": "task-source-boundary",
              "modelMode": "B1",
              "modelVersion": "medkernel-qwen25:1.5b-v1",
              "capabilityCode": "knowledge.production.knowledge",
              "modelOutput": {
                "domain": "GUIDELINE",
                "subject": "指南来源治理与使用边界",
                "clinicalActionable": false,
                "sourceReferences": [
                  {
                    "sourceRef": "WHO-GRC-2026:2026.06.22:page:mandate",
                    "authorityLevel": "B_GUIDELINE",
                    "anchorLabel": "指南质量保障职责"
                  }
                ],
                "limitations": [
                  "仅用于验证 MedKernel 知识生产流程，不构成诊断、处方、剂量、阈值或自动医嘱。"
                ],
                "sections": {
                  "summary": "来源边界：只说明指南来源治理职责；证据不足时明确不可推断。",
                  "references": "正式临床内容仍须绑定具体原始文件、机构版本和适用范围。"
                }
              }
            }
            """;
        return new KnowledgeAssetEnvelope(
            VersionedAssetType.KNOWLEDGE, "launch.guideline.governance-boundary", "指南来源治理与使用边界",
            "ai-draft-task-source-boundary",
            List.of(new AssetSourceRef("WHO-GRC-2026:2026.06.22:page:mandate",
                SourceAuthorityLevel.B_GUIDELINE)),
            SourceAuthorityLevel.B_GUIDELINE, null, null, KnowledgeRiskLevel.LOW, "tenant-a",
            "b".repeat(64), payload, AssetVersionStatus.DRAFT);
    }

    private MedicalRegressionCase regressionCase(String capabilityCode, String expected,
                                                 String redLineType, String citationRequired) {
        return regressionCase(capabilityCode, expected, redLineType, citationRequired, "SRC:v1:section-1");
    }

    private MedicalRegressionCase regressionCase(String capabilityCode, String expected,
                                                 String redLineType, String citationRequired,
                                                 String sourceReference) {
        return new MedicalRegressionCase(null, "tenant-a", capabilityCode, "general", "输入", expected,
            "[]", "[]", 100, redLineType, sourceReference, citationRequired, "v1", "Y",
            Instant.EPOCH, "seed", Instant.EPOCH, "seed");
    }
}
