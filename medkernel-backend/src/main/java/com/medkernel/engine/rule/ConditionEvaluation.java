package com.medkernel.engine.rule;

import java.util.List;

/**
 * 统一条件求值结果。
 */
public record ConditionEvaluation(
    boolean matched,
    List<ConditionEvidence> evidence
) {
    public ConditionEvaluation {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public boolean unknown() {
        return evidence.stream().anyMatch(ConditionEvidence::missing);
    }
}
