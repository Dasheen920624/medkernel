package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 命中核心：强候选+证据+缺失、排除标记、必需缺失降级，全确定性可复现。 */
class DiagnosisMatcherTest {

    private final DiagnosisMatcher matcher = new DiagnosisMatcher(new DiagnosisConfidenceEvaluator());
    private final DiagnosisConfidencePolicy policy = new DiagnosisConfidencePolicy(
        1L, "t-1", "DEFAULT", 2, true, 1, null, "u", null, "u", null);

    private DiagnosisCriterion crit(String code, DiagnosisDirection dir, DiagnosisWeight w) {
        return new DiagnosisCriterion(null, "t-1", 10L, code, dir, w, null, null, null,
            Instant.now(), "u", Instant.now(), "u", "tr");
    }

    @Test
    void strongCandidateWithEvidenceAndMissing() {
        var criteria = List.of(
            crit("FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit("COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR),
            crit("CRP_HIGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR),
            crit("RASH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MINOR));
        var result = matcher.match(Set.of("FEVER", "COUGH", "CRP_HIGH"), criteria, policy);
        assertThat(result.confidence()).isEqualTo(DiagnosisConfidence.STRONG);
        assertThat(result.supporting()).contains("FEVER", "COUGH", "CRP_HIGH");
        assertThat(result.missingRequired()).isEmpty();
        assertThat(result.hitExclusion()).isFalse();
    }

    @Test
    void exclusionMarksExclude() {
        var criteria = List.of(crit("NEG_MARKER", DiagnosisDirection.EXCLUSION, DiagnosisWeight.MAJOR));
        var result = matcher.match(Set.of("NEG_MARKER"), criteria, policy);
        assertThat(result.confidence()).isEqualTo(DiagnosisConfidence.EXCLUDE);
        assertThat(result.hitExclusion()).isTrue();
    }

    @Test
    void missingRequiredLowersToWeak() {
        var criteria = List.of(
            crit("FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit("COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR));
        var result = matcher.match(Set.of("COUGH"), criteria, policy);
        assertThat(result.confidence()).isEqualTo(DiagnosisConfidence.WEAK);
        assertThat(result.missingRequired()).contains("FEVER");
    }
}
