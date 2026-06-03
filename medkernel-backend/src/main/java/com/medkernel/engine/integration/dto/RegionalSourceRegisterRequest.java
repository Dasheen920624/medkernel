package com.medkernel.engine.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 区域协同来源登记请求。
 */
public record RegionalSourceRegisterRequest(
    @NotBlank @Size(max = 64) String sourceId,
    @NotBlank @Size(max = 256) String regionalNetworkName,
    @NotBlank @Size(max = 64) String sourceOrganizationId,
    @NotBlank @Size(max = 256) String sourceOrganizationName,
    @Size(max = 16) String trustLevel,
    @NotBlank @Size(max = 1000) String evidenceText,
    @Size(max = 64) String adapterId,
    @Size(max = 64) String onboardingId,
    @NotBlank @Size(max = 512) String orgPath
) {
}
