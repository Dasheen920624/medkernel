package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

class ClinicalUnitConversionRegistryTest {

    @Test
    void sameUnitNormalizesCommonUcumTextWithoutChangingMeaning() {
        assertThat(ClinicalUnitConversionRegistry.sameUnit(" umol / L ", "μmol/L")).isTrue();
    }

    @Test
    void convertsGlucoseMgDlToMmolLWithReadableFormula() {
        ClinicalUnitConversionRegistry.ConversionResult result = ClinicalUnitConversionRegistry.convert(
            "lab.glucose", new BigDecimal("130"), "mg/dL", "mmol/L", "glucose");

        assertThat(result.value()).isEqualByComparingTo("7.21492713");// 原始精度保留给后续比较
        assertThat(result.formula()).isEqualTo("130 mg/dL / 18.0182 = 7.21 mmol/L");
    }

    @Test
    void convertsCreatinineUmolLToMgDlForControlledFormulaInputs() {
        ClinicalUnitConversionRegistry.ConversionResult result = ClinicalUnitConversionRegistry.convert(
            "derived.egfr.creatinine", new BigDecimal("88.4"), "umol/L", "mg/dL", "creatinine");

        assertThat(result.value()).isEqualByComparingTo("1.0");
        assertThat(result.formula()).isEqualTo("88.4 umol/L / 88.4 = 1 mg/dL");
    }

    @Test
    void rejectsUnknownAnalyteConversionWithoutGuessing() {
        assertThatThrownBy(() -> ClinicalUnitConversionRegistry.convert(
            "lab.sodium", new BigDecimal("140"), "mmol/L", "mg/dL", "sodium"))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNIT_INCOMPATIBLE);
                assertThat(exception.getMessage()).contains("lab.sodium", "sodium", "mmol/L", "mg/dL");
            });
    }
}
