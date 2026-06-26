package com.medkernel.engine.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SandboxScenarioCatalogTest {

    private final SandboxScenarioCatalog catalog = new SandboxScenarioCatalog();

    @Test
    void resolvesCriticalPotassiumScenario() {
        SandboxScenario scenario = catalog.require("sbx-lab-critical-k");

        assertThat(scenario.triggerPoint()).isEqualTo("result-review");
        assertThat(scenario.expectedRuleCode()).isEqualTo("SBX.LAB.CRITICAL.K");
        assertThat(scenario.patientId()).isNotBlank();
        assertThat(SandboxScenario.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("packageVersion", "status", "statusReason");
    }

    @Test
    void unknownScenarioThrows() {
        assertThatThrownBy(() -> catalog.require("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未知沙盘场景");
    }

    @Test
    void registersTheCompleteScenarioMatrixAndAllTenRuleScenariosReachRuntimeResolution() {
        assertThat(catalog.all()).hasSize(15);
        assertThat(catalog.all())
            .filteredOn(scenario -> "rule".equals(scenario.engine()))
            .extracting(SandboxScenario::id)
            .containsExactly(
                "sbx-lab-critical-k",
                "sbx-med-warfarin-asa",
                "sbx-order-contrast-ckd",
                "sbx-dx-acs",
                "sbx-report-critical",
                "sbx-discharge-check",
                "sbx-followup-inr",
                "sbx-insurance-drg",
                "sbx-quality-record",
                "sbx-record-completeness");
        assertThat(catalog.all())
            .filteredOn(scenario -> !"rule".equals(scenario.engine()))
            .extracting(SandboxScenario::id)
            .containsExactly(
                "sbx-pathway-cycle",
                "sbx-recommendation-composite",
                "sbx-followup-closed-loop",
                "sbx-evaluation-closed-loop",
                "sbx-embed-modes");
        assertThat(catalog.all()).allSatisfy(scenario ->
            assertThat(catalog.requireRunnable(scenario.id())).isSameAs(scenario));
        assertThat(catalog.require("sbx-pathway-cycle").expectedAssetCode())
            .isEqualTo("PATH.CLINICAL.CYCLE");
        assertThat(catalog.require("sbx-recommendation-composite").expectedAction())
            .isEqualTo("SUGGEST_ORDER");
    }

    @Test
    void scenarioCatalogDoesNotCarryAStaticClinicalReviewBlock() {
        assertThat(catalog.requireRunnable("sbx-med-warfarin-asa").expectedRuleCode())
            .isEqualTo("SBX.MED.WARFARIN.ASA");
    }
}
