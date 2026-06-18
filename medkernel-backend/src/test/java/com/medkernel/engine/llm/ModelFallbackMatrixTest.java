package com.medkernel.engine.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    void externalModelCanFallBackToLocalModelWhenOrderAllowsIntermediateLevel() {
        ModelFallbackDecision decision =
            matrix.decide("EXTERNAL_MODEL", "LOCAL_MODEL", ModelFallbackTrigger.PROVIDER_RATE_LIMITED, "429");

        assertThat(decision.sourceMode()).isEqualTo("B2");
        assertThat(decision.fallbackMode()).isEqualTo("B1");
        assertThat(decision.fallbackUsed()).isTrue();
        assertThat(decision.reason())
            .contains("PROVIDER_RATE_LIMITED")
            .contains("B2 -> B1")
            .doesNotContain("B0 确定性基线");
    }

    @Test
    void baselineRouteUsesExplicitB0Reason() {
        ModelFallbackDecision decision =
            matrix.decide("BASELINE", ModelFallbackTrigger.POLICY_BASELINE, "策略指定");

        assertThat(decision.fallbackMode()).isEqualTo("B0");
        assertThat(decision.reason()).contains("POLICY_BASELINE");
        assertThat(decision.retryable()).isFalse();
    }

    @ParameterizedTest(name = "{0} + {1} -> B0")
    @MethodSource("runtimeMatrix")
    void fourRuntimeTriggersByThreeModelLevelsHaveAuditableB0Outcome(
            String routeStrategy,
            String expectedSourceMode,
            ModelFallbackTrigger trigger,
            boolean expectedRetryable) {
        ModelFallbackDecision decision = matrix.decide(routeStrategy, trigger, "matrix-proof");

        assertThat(decision.routeStrategy()).isEqualTo(routeStrategy);
        assertThat(decision.sourceMode()).isEqualTo(expectedSourceMode);
        assertThat(decision.fallbackMode()).isEqualTo("B0");
        assertThat(decision.reason())
            .contains("LLM-02")
            .contains(trigger.name())
            .contains(expectedSourceMode + " -> B0")
            .contains("B0 确定性基线")
            .contains("matrix-proof");
        assertThat(decision.retryable()).isEqualTo(expectedRetryable);
        if ("B0".equals(expectedSourceMode)) {
            assertThat(decision.fallbackUsed()).isFalse();
        } else {
            assertThat(decision.fallbackUsed()).isTrue();
        }
    }

    private static Stream<Arguments> runtimeMatrix() {
        return Stream.of(
            row("BASELINE", "B0"),
            row("LOCAL_MODEL", "B1"),
            row("EXTERNAL_MODEL", "B2")
        ).flatMap(stream -> stream);
    }

    private static Stream<Arguments> row(String routeStrategy, String expectedSourceMode) {
        return Stream.of(
            Arguments.of(routeStrategy, expectedSourceMode, ModelFallbackTrigger.PROVIDER_TIMEOUT, true),
            Arguments.of(routeStrategy, expectedSourceMode, ModelFallbackTrigger.PROVIDER_RATE_LIMITED, true),
            Arguments.of(routeStrategy, expectedSourceMode, ModelFallbackTrigger.STRUCTURED_OUTPUT_FAILED, false),
            Arguments.of(routeStrategy, expectedSourceMode, ModelFallbackTrigger.PROVIDER_DISCONNECTED, true)
        );
    }
}
