package com.medkernel.engine.llm.egress;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 脱敏后模型出域责任确认请求。
 */
public record ModelEgressConfirmationRequest(
    @NotBlank String capabilityCode,
    @NotBlank String payloadHash,
    @NotBlank @Size(max = 512) String purpose
) {}
