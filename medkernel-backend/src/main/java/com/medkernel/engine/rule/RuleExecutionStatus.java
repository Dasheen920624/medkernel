package com.medkernel.engine.rule;

/**
 * 规则一次执行的终态枚举（GA-ENG-API-05 写入 {@code rule_execution_log.status}）。
 *
 * <p>取值含义：{@code SUCCESS} 命中并产出动作、{@code MISS} DSL 条件未命中、
 * {@code SUPPRESSED} 被已命中的高阶规则抑制、{@code DEDUPLICATED} 在同患者同语义窗口内去重、
 * {@code FAILED} 执行异常。
 */
public enum RuleExecutionStatus {
    SUCCESS,
    MISS,
    SUPPRESSED,
    DEDUPLICATED,
    FAILED
}
