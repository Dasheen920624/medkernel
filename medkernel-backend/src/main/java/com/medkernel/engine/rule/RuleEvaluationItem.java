package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 规则单次评估项（GA-ENG-API-05）：一条命中或未命中明细，包含执行 ID、严重度、动作清单与解释快照。
 */
public record RuleEvaluationItem(
    String executionId,
    String ruleId,
    String versionId,
    boolean hit,
    RuleRiskLevel severity,
    List<RuleActionResult> actions,
    JsonNode explanation,
    RuleExecutionStatus status,
    String suppressedBy,
    String deduplicatedFromExecutionId
) {
    public RuleEvaluationItem {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
