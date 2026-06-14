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
        assertThat(scenario.status()).isEqualTo(SandboxScenarioStatus.READY);
    }

    @Test
    void unknownScenarioThrows() {
        assertThatThrownBy(() -> catalog.require("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未知沙盘场景");
    }

    @Test
    void registersTheCompleteScenarioMatrixWithRunnableOuterEngines() {
        assertThat(catalog.all()).hasSize(15);
        assertThat(catalog.all())
            .filteredOn(scenario -> scenario.status() == SandboxScenarioStatus.READY)
            .extracting(SandboxScenario::id)
            .containsExactly(
                "sbx-lab-critical-k",
                "sbx-pathway-ed",
                "sbx-recommendation-composite",
                "sbx-followup-closed-loop",
                "sbx-evaluation-closed-loop",
                "sbx-embed-modes");
        assertThat(catalog.all())
            .filteredOn(scenario ->
                scenario.status() == SandboxScenarioStatus.CLINICAL_REVIEW_REQUIRED)
            .hasSize(9)
            .allSatisfy(scenario ->
                assertThat(scenario.statusReason()).contains("临床评审"));
        assertThat(catalog.require("sbx-pathway-ed").expectedAssetCode())
            .isEqualTo("PATH.ED.DISPOSITION");
        assertThat(catalog.require("sbx-recommendation-composite").expectedAction())
            .isEqualTo("SUGGEST_ORDER");
    }

    @Test
    void blockedScenarioCannotEnterOrchestration() {
        assertThatThrownBy(() -> catalog.requireRunnable("sbx-med-warfarin-asa"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("临床评审");
    }
}
