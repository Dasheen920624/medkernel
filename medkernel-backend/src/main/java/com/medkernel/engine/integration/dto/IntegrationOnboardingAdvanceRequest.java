package com.medkernel.engine.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 第三方业务接口接入阶段推进请求。
 */
public record IntegrationOnboardingAdvanceRequest(
    @NotBlank
    @Pattern(regexp = "REQUESTED|AUTH_CONFIGURED|MAPPING_CONFIGURED|ONLINE|OFFLINE")
    String targetStatus,
    @NotBlank @Size(max = 1000) String evidenceText
) {
}
