package com.medkernel.engine.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * LLM-02 降级矩阵测试。
 *
 * <p>四类运行期触发都必须诚实回退到 B0，且归因码稳定，便于审计和前端展示。
 */
class ModelFallbackMatrixTest {

    private final ModelFallbackMatrix matrix = new ModelFallbackMatrix();

    @Test
    void externalModelTriggersDegradeToB0WithStableReasonCodes() {
        assertThat(matrix.decide("EXTERNAL_MODEL", ModelFallbackTrigger.PROVIDER_TIMEOUT, "30s").reason())
            .contains("PROVIDER_TIMEOUT")
            .contains("B2");
        assertThat(matrix.decide("EXTERNAL_MODEL", ModelFallbackTrigger.PROVIDER_RATE_LIMITED, "429").reason())
            .contains("PROVIDER_RATE_LIMITED")
            .contains("B2");
        assertThat(matrix.decide("EXTERNAL_MODEL", ModelFallbackTrigger.STRUCTURED_OUTPUT_FAILED, "schema").reason())
            .contains("STRUCTURED_OUTPUT_FAILED")
            .contains("B2");
        assertThat(matrix.decide("EXTERNAL_MODEL", ModelFallbackTrigger.PROVIDER_DISCONNECTED, "断连").reason())
            .contains("PROVIDER_DISCONNECTED")
            .contains("B2");
    }

    @Test
    void localModelTriggersDegradeToB0WithoutPretendingExternalProvider() {
        ModelFallbackDecision decision =
            matrix.decide("LOCAL_MODEL", ModelFallbackTrigger.PROVIDER_DISCONNECTED, "ollama not connected");

        assertThat(decision.fallbackMode()).isEqualTo("B0");
        assertThat(decision.fallbackUsed()).isTrue();
        assertThat(decision.reason()).contains("B1").contains("PROVIDER_DISCONNECTED");
        assertThat(decision.reason()).doesNotContain("B2");
    }

    @Test
    void baselineRouteUsesExplicitB0Reason() {
        ModelFallbackDecision decision =
            matrix.decide("BASELINE", ModelFallbackTrigger.POLICY_BASELINE, "策略指定");

        assertThat(decision.fallbackMode()).isEqualTo("B0");
        assertThat(decision.reason()).contains("POLICY_BASELINE");
        assertThat(decision.retryable()).isFalse();
    }
}
