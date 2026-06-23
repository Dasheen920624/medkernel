package com.medkernel.engine.rule;

import java.util.List;
import java.util.Set;

/**
 * 临床受控公式白名单。
 *
 * <p>规则 DSL 只允许调用本注册表声明的确定性公式，禁止运行期任意表达式注入。
 */
public final class ClinicalFunctionRegistry {

    static final String CKD_EPI_2021_EGFR = "CKD_EPI_2021_EGFR";
    static final String COCKCROFT_GAULT_CRCL = "COCKCROFT_GAULT_CRCL";
    static final String MOSTELLER_BSA = "MOSTELLER_BSA";
    static final String BMI = "BMI";

    private static final Set<String> SUPPORTED = Set.of(
        CKD_EPI_2021_EGFR,
        COCKCROFT_GAULT_CRCL,
        MOSTELLER_BSA,
        BMI
    );

    private ClinicalFunctionRegistry() {
    }

    public static boolean isSupported(String formulaName) {
        return SUPPORTED.contains(formulaName);
    }

    public static List<String> supportedFormulaNames() {
        return List.copyOf(SUPPORTED);
    }
}
