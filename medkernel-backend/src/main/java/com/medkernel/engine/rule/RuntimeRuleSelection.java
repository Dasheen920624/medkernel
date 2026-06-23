package com.medkernel.engine.rule;

import java.util.List;

/**
 * 指定临床触发点在医院运行修订内可执行的精确规则集合。
 */
public record RuntimeRuleSelection(
    String runtimeReleaseId,
    String platformBaselineReleaseId,
    List<RuntimeRuleReference> rules
) {
    public RuntimeRuleSelection {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
