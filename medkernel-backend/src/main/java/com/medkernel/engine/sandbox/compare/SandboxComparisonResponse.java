package com.medkernel.engine.sandbox.compare;

import java.util.List;

/** 同一脱敏上下文上的历史基线与当前基线差异。 */
public record SandboxComparisonResponse(
    String contextHash,
    SandboxComparisonSummary summary,
    List<SandboxRuleComparison> differences,
    int unchangedCount
) {
    public SandboxComparisonResponse {
        differences = differences == null ? List.of() : List.copyOf(differences);
    }
}
