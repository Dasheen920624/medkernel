package com.medkernel.engine.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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

import com.medkernel.engine.llm.provider.ModelProviderRegistry;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 医学回归评测服务单元测试（LLM-07 T16/T17/T18 编排）。
 */
class ModelEvalServiceTest {

    private MedicalRegressionCaseRepository caseRepo;
    private ModelEvalRunRepository runRepo;
    private MedicalRegressionEvaluator evaluator;
    private ModelProviderRegistry registry;
    private AuditRecorder auditRecorder;
    private ModelEvalService service;

    @BeforeEach
    void setUp() {
        caseRepo = mock(MedicalRegressionCaseRepository.class);
        runRepo = mock(ModelEvalRunRepository.class);
        evaluator = mock(MedicalRegressionEvaluator.class);
        registry = mock(ModelProviderRegistry.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new ModelEvalService(caseRepo, runRepo, evaluator, registry, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("t", OrgScope.tenant("tenant-1"), "quality-001"));
        when(runRepo.save(any(ModelEvalRun.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private MedicalRegressionCase aCase() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new MedicalRegressionCase(1L, "tenant-1", "rule.draft", "general", "输入", "期望",
            "[]", "[]", 100, null, "source-version:1", "N", "v1", "Y", now, "s", now, "s");
    }

    @Test
    void runEvaluation_noCases_recordsFailedRunWithoutCertifying() {
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "rule.draft", "Y"))
            .thenReturn(List.of());

        ModelEvalRun run = service.runEvaluation("ollama-local", "qwen2.5:7b", "rule.draft");

        // 无基准集不得自动认证，必须 FAILED
        assertThat(run.status()).isEqualTo("FAILED");
    }

    @Test
    void runEvaluation_persistsEvaluatorVerdict() {
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "rule.draft", "Y"))
            .thenReturn(List.of(aCase()));
        var adapter = mock(com.medkernel.engine.llm.provider.ModelProvider.class);
        var config = mock(com.medkernel.engine.llm.provider.ModelProviderConfig.class);
        when(registry.resolveByCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(new ModelProviderRegistry.ResolvedProvider(adapter, config)));
        when(evaluator.evaluate(any(), any()))
            .thenReturn(new MedicalRegressionEvaluator.EvalVerdict(1, 1, 0, false, false, "PASSED"));

        ModelEvalRun run = service.runEvaluation("ollama-local", "qwen2.5:7b", "rule.draft");

        assertThat(run.status()).isEqualTo("PASSED");
        verify(runRepo).save(argThat(r -> "PASSED".equals(r.status())
            && "ollama-local".equals(r.providerCode()) && r.passedCases() == 1));
    }

    @Test
    void runQualityEvaluation_persistsHallucinationFailureAndVersionTriple() {
        service = new ModelEvalService(caseRepo, runRepo, new MedicalRegressionEvaluator(), registry, auditRecorder);
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        MedicalRegressionCase regCase = new MedicalRegressionCase(
            7L, "tenant-1", "recommendation.draft", "terminology",
            "请输出推荐解释", "建议人工复核", "[\"慢性肾脏病\"]",
            "[\"虚构医保编码 ZZZ-2026\"]", 80, null,
            "source-version:77#term", "Y", "2026.06", "Y", now, "s", now, "s");
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(
                "tenant-1", "recommendation.draft", "Y"))
            .thenReturn(List.of(regCase));

        ModelEvalRun run = service.runQualityEvaluation(new AiQualityEvalRunRequest(
            "recommendation.draft",
            "b0-fixture",
            "B0-Deterministic-Baseline",
            "prompt:v1",
            "tool:v1",
            List.of(new AiQualityEvalCaseOutput(
                7L,
                "建议人工复核。慢性肾脏病。虚构医保编码 ZZZ-2026。",
                "B0-Deterministic-Baseline",
                0.88,
                "source-version:77#term"))));

        assertThat(run.status()).isEqualTo("FAILED");
        assertThat(run.capabilityCode()).isEqualTo("recommendation.draft");
        assertThat(run.promptVersion()).isEqualTo("prompt:v1");
        assertThat(run.toolVersion()).isEqualTo("tool:v1");
        assertThat(run.hallucinationDetected()).isEqualTo("Y");
        assertThat(run.qualityScore()).isLessThan(80.0);
        assertThat(run.terminologyScore()).isEqualTo(100.0);
        assertThat(run.caseSummaryJson()).contains("HALLUCINATION_FORBIDDEN_ASSERTION");
    }

    @Test
    void runQualityEvaluation_rejectsMismatchedOfflineOutputModelVersion() {
        service = new ModelEvalService(caseRepo, runRepo, new MedicalRegressionEvaluator(), registry, auditRecorder);

        assertThatThrownBy(() -> service.runQualityEvaluation(new AiQualityEvalRunRequest(
            "recommendation.draft",
            "b0-fixture",
            "B0-Deterministic-Baseline",
            "prompt:v1",
            "tool:v1",
            List.of(new AiQualityEvalCaseOutput(
                7L,
                "建议人工复核。",
                "other-model",
                0.88,
                "source-version:77#term")))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("model_version");
        verify(runRepo, never()).save(any(ModelEvalRun.class));
    }

    @Test
    void qualityTrend_returnsRecentQualityRunsForCapabilityAndModelVersion() {
        Instant newest = Instant.parse("2026-06-14T00:05:00Z");
        Instant older = Instant.parse("2026-06-14T00:00:00Z");
        when(runRepo.findTop20ByTenantIdAndCapabilityCodeAndModelVersionOrderByCreatedAtDesc(
                "tenant-1", "recommendation.draft", "B0-Deterministic-Baseline"))
            .thenReturn(List.of(
                trendRun(12L, newest, 96.0, 100.0, "N", "PASSED", "prompt:v2", "tool:v2"),
                trendRun(11L, older, 72.0, 80.0, "Y", "FAILED", "prompt:v1", "tool:v1")));

        AiQualityTrendResponse trend = service.qualityTrend(
            "recommendation.draft", "B0-Deterministic-Baseline");

        assertThat(trend.capabilityCode()).isEqualTo("recommendation.draft");
        assertThat(trend.modelVersion()).isEqualTo("B0-Deterministic-Baseline");
        assertThat(trend.points()).extracting(AiQualityTrendPoint::runId).containsExactly(12L, 11L);
        assertThat(trend.points().get(0).qualityScore()).isEqualTo(96.0);
        assertThat(trend.points().get(1).hallucinationDetected()).isTrue();
    }

    @Test
    void signOff_pendingReviewBecomesPassedWithReviewer() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(runRepo.findById(9L)).thenReturn(Optional.of(new ModelEvalRun(9L, "tenant-1",
            "claude-prod", "claude-opus-4-8", "rule.draft", "prompt:v1", "tool:v1",
            5, 5, 0, null, null, "N", "N", "N", "PENDING_REVIEW", "[]",
            null, null, now, "s", now, "s")));

        ModelEvalRun signed = service.signOff(9L);

        assertThat(signed.status()).isEqualTo("PASSED");
        assertThat(signed.reviewer()).isEqualTo("quality-001");
        assertThat(signed.signedAt()).isNotNull();
    }

    @Test
    void signOff_nonPendingRejected() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(runRepo.findById(9L)).thenReturn(Optional.of(new ModelEvalRun(9L, "tenant-1",
            "claude-prod", "claude-opus-4-8", "rule.draft", "prompt:v1", "tool:v1",
            5, 4, 1, null, null, "N", "N", "N", "FAILED", "[]",
            null, null, now, "s", now, "s")));

        assertThatThrownBy(() -> service.signOff(9L)).isInstanceOf(ApiException.class);
    }

    @Test
    void isClearedForGoLive_trueWhenPassedRunExists() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(runRepo.findFirstByTenantIdAndProviderCodeAndModelVersionAndStatusOrderByIdDesc(
                "tenant-1", "ollama-local", "qwen2.5:7b", "PASSED"))
            .thenReturn(Optional.of(new ModelEvalRun(1L, "tenant-1", "ollama-local", "qwen2.5:7b",
                "rule.draft", "prompt:v1", "tool:v1",
                3, 3, 0, null, null, "N", "N", "N", "PASSED", "[]",
                "quality-001", now, now, "s", now, "s")));

        assertThat(service.isClearedForGoLive("tenant-1", "ollama-local", "qwen2.5:7b")).isTrue();
        assertThat(service.isClearedForGoLive("tenant-1", "ollama-local", "other-version")).isFalse();
    }

    private ModelEvalRun trendRun(
            Long id,
            Instant createdAt,
            double qualityScore,
            double terminologyScore,
            String hallucinationDetected,
            String status,
            String promptVersion,
            String toolVersion) {
        return new ModelEvalRun(
            id, "tenant-1", "b0-fixture", "B0-Deterministic-Baseline",
            "recommendation.draft", promptVersion, toolVersion,
            3, "PASSED".equals(status) ? 3 : 2, "PASSED".equals(status) ? 0 : 1,
            qualityScore, terminologyScore, "N", "N", hallucinationDetected,
            status, "[]", null, null, createdAt, "s", createdAt, "s");
    }
}
