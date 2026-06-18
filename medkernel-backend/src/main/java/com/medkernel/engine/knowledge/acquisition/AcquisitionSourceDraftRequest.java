package com.medkernel.engine.knowledge.acquisition;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.parsing.DocumentFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 公域资料来源待审批草稿。保存草稿不会启用来源，任何实质变更都会撤销既有审批。
 */
public record AcquisitionSourceDraftRequest(
    @NotBlank @Size(max = 255) String domain,
    @NotBlank @Size(max = 512) String baseUrl,
    @NotNull SourceType sourceType,
    @NotNull SourceAuthorityLevel authorityLevel,
    @NotBlank @Size(max = 512) String authorityBasis,
    @NotBlank @Size(max = 512) String title,
    @NotBlank @Size(max = 256) String publisher,
    @NotBlank @Size(max = 512) String license,
    @NotNull AcquisitionLicensePolicy licensePolicy,
    @NotNull AcquisitionRobotsPolicy robotsPolicy,
    Boolean scheduleEnabled,
    @Min(1) Integer scheduleIntervalMinutes,
    DocumentFormat defaultFormat,
    @Valid AcquisitionCandidateGenerationRequest generationPlan
) {
    public boolean scheduleRequested() {
        return Boolean.TRUE.equals(scheduleEnabled);
    }
}
