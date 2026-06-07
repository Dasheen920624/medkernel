package com.medkernel.engine.rule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 规则影子命中人工复核请求。
 */
public record RuleShadowFeedbackRequest(
    @NotNull RuleShadowFeedbackDecision decision,
    @Size(max = 500) String reason
) {}
