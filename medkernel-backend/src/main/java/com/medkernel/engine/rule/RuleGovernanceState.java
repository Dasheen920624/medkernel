package com.medkernel.engine.rule;

/**
 * 规则知识治理闭集状态。
 */
public enum RuleGovernanceState {
    DRAFT,
    PEER_REVIEW,
    COMMITTEE,
    SHADOW,
    CANARY,
    FULL,
    MONITOR,
    RETIRED
}
