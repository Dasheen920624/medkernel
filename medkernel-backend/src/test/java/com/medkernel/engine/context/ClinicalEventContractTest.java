package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * API-02 临床事件客户面契约锁。
 */
class ClinicalEventContractTest {

    @Test
    void triggerPointCoversCdsHooksSixEntryPoints() {
        assertThat(ClinicalEventTriggerPoint.values())
            .extracting(ClinicalEventTriggerPoint::wireValue)
            .containsExactlyInAnyOrder(
                "patient-view",
                "order-sign",
                "medication-prescribe",
                "result-review",
                "discharge-sign",
                "followup-alert");
    }
}
