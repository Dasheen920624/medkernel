package com.medkernel.engine.factory;

/**
 * 临床公式输入项定义。
 *
 * @param key 输入项稳定键
 * @param unit 单位标签
 * @param required 是否必填
 */
public record ClinicalFormulaInput(String key, String unit, boolean required) {
}
