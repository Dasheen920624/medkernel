package com.medkernel.engine.factory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 临床公式计算结果。
 *
 * @param code 公式代码
 * @param score 计算结果；缺必填输入时为空
 * @param missingInputs 缺失必填输入
 * @param executable 是否完成计算
 */
public record ClinicalFormulaResult(
    String code,
    BigDecimal score,
    List<String> missingInputs,
    boolean executable
) {
    public ClinicalFormulaResult {
        missingInputs = List.copyOf(missingInputs);
    }
}
