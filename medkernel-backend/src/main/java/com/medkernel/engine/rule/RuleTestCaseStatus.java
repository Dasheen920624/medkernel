package com.medkernel.engine.rule;

/**
 * 规则验证用例最近一次执行状态枚举（GA-ENG-API-05）。
 *
 * <p>取值含义：{@code NOT_RUN} 尚未运行、{@code PASS} 与期望一致、{@code FAIL} 与期望不一致、
 * {@code ERROR} 执行异常；发布门禁要求所有用例必须 PASS。
 */
public enum RuleTestCaseStatus {
    NOT_RUN,
    PASS,
    FAIL,
    ERROR
}
