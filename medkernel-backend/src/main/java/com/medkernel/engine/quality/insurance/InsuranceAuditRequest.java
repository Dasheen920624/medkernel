package com.medkernel.engine.quality.insurance;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 医保审核请求。
 */
public record InsuranceAuditRequest(
    @NotBlank String contextSnapshotId,
    @NotBlank String scenarioCode,
    @NotBlank String indicatorId,
    @NotBlank String responsibleDepartmentId,
    @NotNull Instant dueAt,
    @NotEmpty List<@Valid InsuranceAuditRuleRequest> rules
) {}
