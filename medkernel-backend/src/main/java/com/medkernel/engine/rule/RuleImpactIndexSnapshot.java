package com.medkernel.engine.rule;

import java.util.List;

/**
 * 规则影响索引快照。
 */
record RuleImpactIndexSnapshot(
    List<RuleImpactObject> affectedPathways,
    List<RuleImpactObject> inPathPatients,
    List<RuleImpactObject> integrationAdapters,
    List<String> unavailableScopes
) {
    RuleImpactIndexSnapshot {
        affectedPathways = affectedPathways == null ? List.of() : List.copyOf(affectedPathways);
        inPathPatients = inPathPatients == null ? List.of() : List.copyOf(inPathPatients);
        integrationAdapters = integrationAdapters == null ? List.of() : List.copyOf(integrationAdapters);
        unavailableScopes = unavailableScopes == null ? List.of() : List.copyOf(unavailableScopes);
    }
}
