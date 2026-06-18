package com.medkernel.engine.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;

/**
 * T7.2 评分量表/计算器确定性算子测试。
 *
 * <p>测试使用非医学 fixture 系数，证明算法来自入参定义而不是硬编码医学常量。
 */
class ClinicalFormulaCalculatorServiceTest {

    private final ClinicalFormulaCalculatorService service = new ClinicalFormulaCalculatorService();

    @Test
    void calculatesFormulaFromProvidedDefinitionWithoutMedicalConstants() {
        ClinicalFormulaResult result = service.calculate(
            definition(),
            Map.of("component_a", new BigDecimal("2"), "component_b", new BigDecimal("3")));

        assertThat(result.executable()).isTrue();
        assertThat(result.missingInputs()).isEmpty();
        assertThat(result.score()).isEqualByComparingTo("7.5");
    }

    @Test
    void returnsMissingInputsInsteadOfInventingDefaults() {
        ClinicalFormulaResult result = service.calculate(
            definition(),
            Map.of("component_a", new BigDecimal("2")));

        assertThat(result.executable()).isFalse();
        assertThat(result.missingInputs()).containsExactly("component_b");
        assertThat(result.score()).isNull();
    }

    @Test
    void rejectsFormulaTermsThatReferenceUnknownInputs() {
        ClinicalFormulaDefinition bad = new ClinicalFormulaDefinition(
            "fixture-score",
            List.of(new ClinicalFormulaInput("component_a", "score", true)),
            List.of(new ClinicalFormulaTerm("missing_component", BigDecimal.ONE)),
            BigDecimal.ZERO);

        assertThatThrownBy(() -> service.calculate(bad, Map.of("component_a", BigDecimal.ONE)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("missing_component");
    }

    private ClinicalFormulaDefinition definition() {
        return new ClinicalFormulaDefinition(
            "fixture-score",
            List.of(
                new ClinicalFormulaInput("component_a", "score", true),
                new ClinicalFormulaInput("component_b", "score", true)),
            List.of(
                new ClinicalFormulaTerm("component_a", new BigDecimal("1.5")),
                new ClinicalFormulaTerm("component_b", new BigDecimal("1.5"))),
            new BigDecimal("0"));
    }
}
