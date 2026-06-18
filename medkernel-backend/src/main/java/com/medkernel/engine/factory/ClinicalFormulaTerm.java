package com.medkernel.engine.factory;

import java.math.BigDecimal;

/**
 * 临床公式线性项。
 *
 * @param inputKey 输入项键
 * @param coefficient 系数，来自真实资产定义，不由代码内置医学常量
 */
public record ClinicalFormulaTerm(String inputKey, BigDecimal coefficient) {
}
