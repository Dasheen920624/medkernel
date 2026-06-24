package com.medkernel.engine.rule;

/**
 * 规则知识治理闭集状态。
 */
public enum RuleGovernanceState {
    DRAFT,
    REVIEWED,
    SHADOW,
    CANARY,
    FULL,
    MONITOR,
    RETIRED
}
