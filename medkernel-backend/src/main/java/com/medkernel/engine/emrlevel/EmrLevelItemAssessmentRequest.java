package com.medkernel.engine.emrlevel;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 电子病历评级标准项与系统能力点映射请求。
 */
public record EmrLevelItemAssessmentRequest(
    @NotBlank String itemCode,
    @NotBlank String itemName,
    @NotNull @Min(4) @Max(6) Integer requiredLevel,
    @NotBlank String capabilityCode,
    @NotBlank String capabilityName,
    @NotNull EmrLevelCapabilityStatus capabilityStatus,
    String evidenceRef,
    String evidenceSummary,
    String responsibleDepartmentId,
    Instant dueAt
) {
}
