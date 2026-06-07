package com.medkernel.engine.rule;

import java.time.Instant;

/**
 * 已落库的规则动作人工越权回执。
 */
public record RuleOverrideResponse(
    String overrideId,
    String executionId,
    String ruleId,
    RuleActionCode actionCode,
    String reason,
    String overriddenBy,
    Instant overriddenAt,
    String traceId
) {}
