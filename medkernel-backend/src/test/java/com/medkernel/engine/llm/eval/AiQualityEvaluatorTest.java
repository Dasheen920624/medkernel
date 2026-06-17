package com.medkernel.engine.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.llm.provider.ProviderCompletion;

/**
 * AI 质量评测器单元测试（OPT-06 FR-1/2/3/4/5）。
 */
class AiQualityEvaluatorTest {

    private final MedicalRegressionEvaluator evaluator = new MedicalRegressionEvaluator();

    @Test
    void forbiddenAssertionMarksHallucinationAndFailsEvenWhenExpectedPhraseMatches() {
        MedicalRegressionCase regCase = qualityCase(
            "recommendation",
            "建议人工复核",
            "[]",
            "[\"国家卫健委编码 XYZ-FAKE-2026\"]",
            80);
        Function<MedicalRegressionCase, ProviderCompletion> answer = ignored ->
            new ProviderCompletion("建议人工复核。国家卫健委编码 XYZ-FAKE-2026。", "B0", 0.91, "[]");

        MedicalRegressionEvaluator.QualityEvalVerdict verdict =
            evaluator.evaluateQuality(List.of(regCase), answer);

        assertThat(verdict.status()).isEqualTo("FAILED");
        assertThat(verdict.hallucinationDetected()).isTrue();
        assertThat(verdict.hallucinationCases()).isEqualTo(1);
        assertThat(verdict.failed()).isEqualTo(1);
        assertThat(verdict.qualityScore()).isLessThan(80.0);
        assertThat(verdict.caseSummaryJson()).contains("HALLUCINATION_FORBIDDEN_ASSERTION");
    }

    private MedicalRegressionCase qualityCase(
            String domain,
            String expectedPhrase,
            String expectedTerms,
            String forbiddenAssertions,
            int minScore) {
        Instant now = Instant.parse("2026-06-17T00:00:00Z");
        return new MedicalRegressionCase(
            1L, "tenant-1", "recommendation.draft", domain, "输入",
            expectedPhrase, expectedTerms, forbiddenAssertions, minScore,
            null, "source-version:1", "N", "2026.06", "Y", now, "u", now, "u");
    }
}
