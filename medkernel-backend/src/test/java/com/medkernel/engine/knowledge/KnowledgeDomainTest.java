package com.medkernel.engine.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** 知识内容域边界测试。 */
class KnowledgeDomainTest {

    @Test
    void containsExactlyElevenContentDomainsWithoutPatientReportInterpretation() {
        assertThat(Arrays.asList(KnowledgeDomain.values()))
            .containsExactlyInAnyOrder(
                KnowledgeDomain.GUIDELINE,
                KnowledgeDomain.DRUG,
                KnowledgeDomain.PATHWAY_KNOWLEDGE,
                KnowledgeDomain.NURSING,
                KnowledgeDomain.DIAGNOSTIC_ITEM,
                KnowledgeDomain.TCM,
                KnowledgeDomain.PROTOCOL,
                KnowledgeDomain.POLICY,
                KnowledgeDomain.LITERATURE,
                KnowledgeDomain.OTHER,
                KnowledgeDomain.DIAGNOSIS);
        assertThat(Arrays.stream(KnowledgeDomain.values()).map(Enum::name))
            .doesNotContain("REPORT", "REPORT_INTERPRETATION");
    }
}
