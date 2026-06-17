package com.medkernel.engine.factory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 临床公式定义。
 *
 * @param code 公式代码
 * @param inputs 输入项定义
 * @param terms 计算项
 * @param intercept 截距，来自真实资产定义
 */
public record ClinicalFormulaDefinition(
    String code,
    List<ClinicalFormulaInput> inputs,
    List<ClinicalFormulaTerm> terms,
    BigDecimal intercept
) {
    public ClinicalFormulaDefinition {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        terms = terms == null ? List.of() : List.copyOf(terms);
        intercept = intercept == null ? BigDecimal.ZERO : intercept;
    }
}
