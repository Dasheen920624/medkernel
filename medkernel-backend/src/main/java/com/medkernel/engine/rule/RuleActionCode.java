package com.medkernel.engine.rule;

/**
 * 规则动作码闭集。
 *
 * <p>动作仅描述提示、阻断建议或记录意图，不直接执行医嘱写入。
 */
public enum RuleActionCode {
    INFO,
    REMIND,
    STRONG_REMINDER,
    BLOCK,
    SUGGEST_ORDER,
    AUTO_DOCUMENT
}
