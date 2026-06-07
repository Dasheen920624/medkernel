package com.medkernel.engine.rule;

/**
 * 规则静态冲突结果。
 */
public record RuleConflict(
    String ruleCode,
    String fact,
    String reason
) {}
