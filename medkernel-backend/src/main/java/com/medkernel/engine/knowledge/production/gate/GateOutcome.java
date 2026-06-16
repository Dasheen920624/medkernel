package com.medkernel.engine.knowledge.production.gate;

import java.util.List;

/**
 * 候选门禁总判定（AIK-STD-05）。
 *
 * <p>{@code passed} 为全部门禁项通过；{@code items} 为逐项结果（含不过原因）。任一项不过则整体不过，候选不提审。
 */
public record GateOutcome(boolean passed, List<GateItemResult> items) {

    public GateOutcome {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 不过的门禁项（供拦截报因）。 */
    public List<GateItemResult> failedItems() {
        return items.stream().filter(item -> !item.passed()).toList();
    }
}
