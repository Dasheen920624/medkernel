package com.medkernel.engine.safety;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * OPT-04 临床安全红线静默试运行证据提交请求。
 */
public record ClinicalRedlineDryRunRequest(
    @NotBlank String redlineId,
    @NotNull Instant observedFrom,
    @NotNull Instant observedTo,
    @Min(0) long evaluatedCaseCount,
    @Min(0) long matchedCaseCount,
    @Min(0) long falsePositiveCaseCount,
    @Min(0) long safetyIncidentCount,
    @NotBlank String evidenceReference,
    String operatorNote
) {}
