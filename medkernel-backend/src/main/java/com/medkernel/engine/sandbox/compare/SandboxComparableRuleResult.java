package com.medkernel.engine.sandbox.compare;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.rule.RuleActionResult;
import com.medkernel.engine.versioning.SourceTier;

/** 沙盘对比两侧统一的规则执行结果，业务键为规则编码。 */
public record SandboxComparableRuleResult(
    String ruleCode,
    String ruleName,
    String versionId,
    String assetVersion,
    SourceTier sourceTier,
    String sourceTenantId,
    String contentHash,
    boolean hit,
    String severity,
    List<RuleActionResult> actions,
    JsonNode explanation
) {
    public SandboxComparableRuleResult {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
