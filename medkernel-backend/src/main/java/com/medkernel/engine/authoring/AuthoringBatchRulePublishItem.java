package com.medkernel.engine.authoring;

import jakarta.validation.constraints.NotBlank;

/**
 * 一条待批量治理推进的规则。
 */
public record AuthoringBatchRulePublishItem(
    @NotBlank String itemId,
    @NotBlank String ruleId,
    @NotBlank String impactDigest,
    boolean highRiskConfirmed
) {}
