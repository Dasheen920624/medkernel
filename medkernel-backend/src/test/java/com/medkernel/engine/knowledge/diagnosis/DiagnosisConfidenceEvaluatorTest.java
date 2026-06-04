package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 置信分级 evaluator：覆盖 4 级 + 可配置阈值；排除优先、必需缺失降级、主要不足转中。 */
class DiagnosisConfidenceEvaluatorTest {

    private final DiagnosisConfidenceEvaluator evaluator = new DiagnosisConfidenceEvaluator();
    // 默认策略：strongMinMajor=2, requireAllRequired=true, moderateMinHits=1
    private final DiagnosisConfidencePolicy policy = new DiagnosisConfidencePolicy(
        1L, "t-1", "DEFAULT", 2, true, 1, null, "u", null, "u", null);

    @Test
    void exclusionWins() {
        var stats = new DiagnosisMatchStats(3, 0, 0, 0, true);
        assertThat(evaluator.evaluate(stats, policy)).isEqualTo(DiagnosisConfidence.EXCLUDE);
    }

    @Test
    void strongWhenAllRequiredAndEnoughMajor() {
        var stats = new DiagnosisMatchStats(2, 0, 1, 1, false); // major=2, required 1/1
        assertThat(evaluator.evaluate(stats, policy)).isEqualTo(DiagnosisConfidence.STRONG);
    }

    @Test
    void weakWhenRequiredMissing() {
        var stats = new DiagnosisMatchStats(2, 0, 2, 1, false); // required 1/2 缺失
        assertThat(evaluator.evaluate(stats, policy)).isEqualTo(DiagnosisConfidence.WEAK);
    }

    @Test
    void moderateWhenRequiredMetButFewMajor() {
        var stats = new DiagnosisMatchStats(1, 1, 1, 1, false);
        assertThat(evaluator.evaluate(stats, policy)).isEqualTo(DiagnosisConfidence.MODERATE);
    }
}
