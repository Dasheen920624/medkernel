package com.medkernel.engine.rule;

import java.time.Instant;

/**
 * 规则影子运行人工复核响应。
 */
public record RuleShadowFeedbackResponse(
    String feedbackId,
    String executionId,
    String ruleId,
    RuleShadowFeedbackDecision decision,
    String reason,
    String assessedBy,
    Instant assessedAt,
    String traceId
) {}
