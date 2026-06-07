package com.medkernel.engine.rule;

/**
 * 规则一次执行的终态枚举（GA-ENG-API-05 写入 {@code rule_execution_log.status}）。
 *
 * <p>取值含义：{@code SUCCESS} 命中并产出动作、{@code MISS} DSL 条件未命中、
 * {@code SHADOW_RECORDED} 影子运行只记录不动作、
 * {@code NOT_APPLICABLE} 当前上下文不在规则适用域、{@code SUPPRESSED} 被已命中的高阶规则抑制、
 * {@code DEDUPLICATED} 在同患者同语义窗口内去重、{@code FAILED} 执行异常。
 */
public enum RuleExecutionStatus {
    SUCCESS,
    SHADOW_RECORDED,
    MISS,
    NOT_APPLICABLE,
    SUPPRESSED,
    DEDUPLICATED,
    FAILED
}
