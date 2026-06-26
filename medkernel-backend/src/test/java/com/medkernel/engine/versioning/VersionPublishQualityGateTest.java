package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionPublishQualityGateTest {

    @Test
    void defaultSummaryUsesImpactAssessmentLanguage() {
        VersionPublishQualityGate gate = new VersionPublishQualityGate(
            true,
            true,
            true,
            true,
            true,
            null
        );

        assertThat(gate.summaryOrDefault())
            .isEqualTo("结构校验、术语字段绑定、依赖完整性、安全单调性、影响评估已通过");
    }
}
