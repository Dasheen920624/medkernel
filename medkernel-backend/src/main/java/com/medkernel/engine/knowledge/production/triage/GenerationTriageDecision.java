package com.medkernel.engine.knowledge.production.triage;

/**
 * AIK-STD-10 单个候选分流判定。
 */
public record GenerationTriageDecision(
    Long triageId,
    GenerationTriageState state,
    GenerationTriageAction action,
    Long activeVersionId,
    Long matchedVersionId,
    String basis
) {
    public boolean shouldSubmit() {
        return action != GenerationTriageAction.SKIP_DUPLICATE;
    }
}
