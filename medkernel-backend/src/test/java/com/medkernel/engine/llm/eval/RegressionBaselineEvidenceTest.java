package com.medkernel.engine.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 医学回归基准集证据指纹测试。
 */
class RegressionBaselineEvidenceTest {

    @Test
    void sameCasesProduceMatchingEvidenceRegardlessOfInputOrder() {
        MedicalRegressionCase first = regressionCase(1L, "输入一", "期望一", "v1");
        MedicalRegressionCase second = regressionCase(2L, "输入二", "期望二", "v1");

        String evidence = RegressionBaselineEvidence.toJson(List.of(first, second));

        assertThat(RegressionBaselineEvidence.matches(evidence, List.of(second, first))).isTrue();
        assertThat(evidence).contains("baselineFingerprint", "caseCount");
    }

    @Test
    void changedMedicalExpectationInvalidatesPriorEvidenceEvenWhenCaseCountIsUnchanged() {
        MedicalRegressionCase before = regressionCase(1L, "输入", "旧期望", "v1");
        MedicalRegressionCase after = regressionCase(1L, "输入", "新期望", "v1");

        String evidence = RegressionBaselineEvidence.toJson(List.of(before));

        assertThat(RegressionBaselineEvidence.matches(evidence, List.of(after))).isFalse();
        assertThat(RegressionBaselineEvidence.matches("[]", List.of(after))).isFalse();
    }

    @Test
    void nullFieldCannotCollideWithLiteralNullMarkerText() {
        MedicalRegressionCase original = regressionCase(1L, "输入", "期望", "v1");
        MedicalRegressionCase nullRedLine = new MedicalRegressionCase(
            original.id(), original.tenantId(), original.capabilityCode(), original.caseDomain(),
            original.caseInput(), original.expectedPhrase(), original.expectedTermsJson(),
            original.forbiddenAssertionsJson(), original.minScore(), null, original.sourceReference(),
            original.citationRequired(), original.caseVersion(), original.enabledFlag(), original.createdAt(),
            original.createdBy(), original.updatedAt(), original.updatedBy());
        MedicalRegressionCase literalMarker = new MedicalRegressionCase(
            nullRedLine.id(),
            nullRedLine.tenantId(),
            nullRedLine.capabilityCode(),
            nullRedLine.caseDomain(),
            nullRedLine.caseInput(),
            nullRedLine.expectedPhrase(),
            nullRedLine.expectedTermsJson(),
            nullRedLine.forbiddenAssertionsJson(),
            nullRedLine.minScore(),
            "<null>",
            nullRedLine.sourceReference(),
            nullRedLine.citationRequired(),
            nullRedLine.caseVersion(),
            nullRedLine.enabledFlag(),
            nullRedLine.createdAt(),
            nullRedLine.createdBy(),
            nullRedLine.updatedAt(),
            nullRedLine.updatedBy());

        String evidence = RegressionBaselineEvidence.toJson(List.of(nullRedLine));

        assertThat(RegressionBaselineEvidence.matches(evidence, List.of(literalMarker))).isFalse();
    }

    private MedicalRegressionCase regressionCase(Long id,
                                                  String input,
                                                  String expected,
                                                  String version) {
        Instant now = Instant.parse("2026-06-18T00:00:00Z");
        return new MedicalRegressionCase(
            id,
            "tenant-1",
            "rule.draft",
            "general",
            input,
            expected,
            "[\"慢性乙型肝炎\"]",
            "[\"自动开嘱\"]",
            100,
            "PRESCRIPTION",
            "WHO-HBV-2024:1#p34",
            "Y",
            version,
            "Y",
            now,
            "author",
            now,
            "author");
    }
}
