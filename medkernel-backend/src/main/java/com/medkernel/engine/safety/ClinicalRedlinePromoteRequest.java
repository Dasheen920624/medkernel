package com.medkernel.engine.safety;

import jakarta.validation.constraints.NotBlank;

/**
 * OPT-04 临床安全红线静默试运行达标后上线请求。
 */
public record ClinicalRedlinePromoteRequest(
    @NotBlank String redlineId,
    @NotBlank String trialId,
    @NotBlank String expectedRedlineVersion,
    @NotBlank String promotionReason
) {}
