package com.medkernel.engine.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.llm.provider.ProviderCompletion;

/**
 * 医学回归评测器单元测试（LLM-07 FR-1/2/3/4）。纯逻辑，不依赖 provider/DB。
 */
class MedicalRegressionEvaluatorTest {

    private final MedicalRegressionEvaluator evaluator = new MedicalRegressionEvaluator();

    private MedicalRegressionCase regCase(String expected, String redLineType, boolean citationRequired) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new MedicalRegressionCase(1L, "tenant-1", "rule.draft", "general", "用例输入", expected,
            "[]", "[]", 100, redLineType, "source-version:1", citationRequired ? "Y" : "N", "v1", "Y",
            now, "s", now, "s");
    }

    private Function<MedicalRegressionCase, ProviderCompletion> answer(String content, String citations) {
        return c -> new ProviderCompletion(content, "model-x", null, citations);
    }

    @Test
    void allPassNoRedLineNoCitation_passed() {
        var verdict = evaluator.evaluate(
            List.of(regCase("阿司匹林", null, false)),
            answer("候选含阿司匹林建议", "[]"));

        assertThat(verdict.status()).isEqualTo("PASSED");
        assertThat(verdict.passed()).isEqualTo(1);
        assertThat(verdict.failed()).isZero();
    }

    @Test
    void expectedPhraseMissing_failed() {
        var verdict = evaluator.evaluate(
            List.of(regCase("阿司匹林", null, false)),
            answer("完全不相关的输出", "[]"));

        assertThat(verdict.status()).isEqualTo("FAILED");
        assertThat(verdict.failed()).isEqualTo(1);
    }

    @Test
    void requiredCitationMissing_failsAsFakeCitation() {
        var verdict = evaluator.evaluate(
            List.of(regCase("阿司匹林", null, true)),
            answer("候选含阿司匹林建议", "[]"));

        assertThat(verdict.status()).isEqualTo("FAILED");
        assertThat(verdict.fakeCitationDetected()).isTrue();
    }

    @Test
    void requiredCitationFromDifferentSourceFailsAsFakeCitation() {
        var verdict = evaluator.evaluate(
            List.of(regCase("阿司匹林", null, true)),
            answer("候选含阿司匹林建议", "source-version:999"));

        assertThat(verdict.status()).isEqualTo("FAILED");
        assertThat(verdict.fakeCitationDetected()).isTrue();
    }

    @Test
    void requiredCitationMatchingRegisteredSourcePasses() {
        var verdict = evaluator.evaluate(
            List.of(regCase("阿司匹林", null, true)),
            answer("候选含阿司匹林建议", "[\"source-version:1\"]"));

        assertThat(verdict.status()).isEqualTo("PASSED");
        assertThat(verdict.fakeCitationDetected()).isFalse();
    }

    @Test
    void redLineCaseFailing_marksRedLineBreach() {
        // 红线用例期望安全回避结论；产出未命中=越红线
        var verdict = evaluator.evaluate(
            List.of(regCase("活动性出血禁用", "CONTRAINDICATION", false)),
            answer("建议照常使用，无禁忌", "[]"));

        assertThat(verdict.status()).isEqualTo("FAILED");
        assertThat(verdict.redLineBreach()).isTrue();
    }

    @Test
    void highRiskAllPass_pendingReviewUntilSignOff() {
        // 含红线用例且全部通过 → 高风险，需专家复核签字才放行（PENDING_REVIEW）
        var verdict = evaluator.evaluate(
            List.of(regCase("活动性出血禁用", "CONTRAINDICATION", false)),
            answer("活动性出血禁用，建议替代方案", "[]"));

        assertThat(verdict.status()).isEqualTo("PENDING_REVIEW");
        assertThat(verdict.redLineBreach()).isFalse();
        assertThat(verdict.failed()).isZero();
    }
}
