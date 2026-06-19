package com.medkernel.engine.sandbox.replay;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.rule.RuleActionResult;
import com.medkernel.engine.versioning.AssetVersionStatus;

/** 历史规则经正式确定性 DSL 内核求值后的只读结果。 */
public record SandboxReplayRuleResult(
    String ruleCode,
    String ruleName,
    String versionId,
    String assetVersion,
    AssetVersionStatus historicalStatus,
    String contentHash,
    boolean hit,
    String severity,
    List<RuleActionResult> actions,
    JsonNode explanation
) {
    public SandboxReplayRuleResult {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
