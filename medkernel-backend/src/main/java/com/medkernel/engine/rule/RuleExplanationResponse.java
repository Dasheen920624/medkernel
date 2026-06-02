package com.medkernel.engine.rule;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 规则执行解释响应。
 *
 * <p>来自 {@code rule_execution_log} 的真实快照，用于客户面解释追溯。
 */
public record RuleExplanationResponse(
    String executionId,
    String ruleId,
    String versionId,
    String triggerPoint,
    String eventId,
    String inputDigest,
    boolean hit,
    RuleRiskLevel severity,
    JsonNode actions,
    JsonNode explanation,
    RuleExecutionStatus status,
    String traceId
) {}
