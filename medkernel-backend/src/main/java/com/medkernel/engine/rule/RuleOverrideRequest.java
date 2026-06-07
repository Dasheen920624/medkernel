package com.medkernel.engine.rule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 规则动作人工越权请求。
 */
public record RuleOverrideRequest(
    @NotNull RuleActionCode actionCode,
    @NotBlank @Size(max = 500) String reason
) {}
