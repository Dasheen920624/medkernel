package com.medkernel.engine.sandbox.compare;

import java.util.List;

/** 按稳定规则编码聚合的一条版本差异结论。 */
public record SandboxRuleComparison(
    String ruleCode,
    String ruleName,
    boolean comparable,
    String nonComparableReason,
    List<SandboxRuleDifferenceType> changes,
    SandboxComparableRuleResult historical,
    SandboxComparableRuleResult current
) {
    public SandboxRuleComparison {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
