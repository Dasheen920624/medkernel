package com.medkernel.engine.rule;

/**
 * 规则验证用例分类枚举（GA-ENG-API-05 发布门禁要求四类齐备）。
 *
 * <p>取值含义：{@code POSITIVE} 阳性命中、{@code NEGATIVE} 阴性未命中、{@code BOUNDARY} 边界场景、
 * {@code CONFLICT} 冲突/相互排斥场景；缺失字段场景在首版以 BOUNDARY/CONFLICT 输入表达。
 */
public enum RuleTestCaseType {
    POSITIVE,
    NEGATIVE,
    BOUNDARY,
    CONFLICT
}
