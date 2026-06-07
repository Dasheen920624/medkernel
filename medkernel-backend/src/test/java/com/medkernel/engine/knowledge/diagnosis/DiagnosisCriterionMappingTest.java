package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** 诊断知识实体 record 字段映射：构造即覆盖字段顺序/类型，锁定后续 Task 6/7/8 依赖的构造签名。 */
class DiagnosisCriterionMappingTest {

    @Test
    void buildsCriterion() {
        var c = new DiagnosisCriterion(null, "t-1", 10L, "TERM-FEVER",
            DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR, null, null, null,
            Instant.now(), "u", Instant.now(), "u", "tr");
        assertThat(c.direction()).isEqualTo(DiagnosisDirection.SUPPORTING);
        assertThat(c.weight()).isEqualTo(DiagnosisWeight.MAJOR);
        assertThat(c.findingTermCode()).isEqualTo("TERM-FEVER");
        assertThat(c.diagnosisVersionId()).isEqualTo(10L);
    }

    @Test
    void buildsDifferential() {
        var d = new DiagnosisDifferential(null, "t-1", 10L, 99L, "胸痛三联鉴别", "D-二聚体/CTA",
            Instant.now(), "u", Instant.now(), "u", "tr");
        assertThat(d.differentialIdentityId()).isEqualTo(99L);
    }

    @Test
    void buildsCarePointer() {
        var p = new DiagnosisCarePointer(null, "t-1", 10L, DiagnosisCarePointerType.PATHWAY,
            DiagnosisCareTargetType.PATHWAY, "PATHWAY-ACS", true, "确诊后进入 ACS 路径",
            Instant.now(), "u", Instant.now(), "u", "tr");
        assertThat(p.pointerType()).isEqualTo(DiagnosisCarePointerType.PATHWAY);
        assertThat(p.targetType()).isEqualTo(DiagnosisCareTargetType.PATHWAY);
        assertThat(p.isSoft()).isTrue();
        assertThat(p.targetRef()).isEqualTo("PATHWAY-ACS");
    }

    @Test
    void buildsTestCase() {
        var tc = new DiagnosisTestCase(null, "t-1", 10L, "CASE-1", "FEVER,COUGH", 7L,
            DiagnosisConfidence.STRONG, Instant.now(), "u", Instant.now(), "u", "tr");
        assertThat(tc.expectedConfidence()).isEqualTo(DiagnosisConfidence.STRONG);
        assertThat(tc.caseCode()).isEqualTo("CASE-1");
    }

    @Test
    void buildsConfidencePolicy() {
        var policy = new DiagnosisConfidencePolicy(1L, "t-1", "DEFAULT", 2, true, 1,
            Instant.now(), "u", Instant.now(), "u", "tr");
        assertThat(policy.strongMinMajor()).isEqualTo(2);
        assertThat(policy.requireAllRequired()).isTrue();
        assertThat(policy.moderateMinHits()).isEqualTo(1);
        assertThat(policy.scopeKey()).isEqualTo("DEFAULT");
    }
}
