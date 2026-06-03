package com.medkernel.engine.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 第三方业务接口接入申请。
 */
public record IntegrationOnboardingCreateRequest(
    @NotBlank @Size(max = 64) String onboardingId,
    @NotBlank @Size(max = 256) String name,
    @NotBlank @Pattern(regexp = "ADAPTER|FHIR") String accessMode,
    @Size(max = 64) String adapterId,
    @Size(max = 16) String fhirVersion,
    @NotBlank @Size(max = 128) String sourceSystem,
    @NotBlank @Size(max = 256) String businessScenario,
    @NotBlank @Size(max = 512) String orgPath,
    @Size(max = 64) String callbackWebhookId
) {
}
