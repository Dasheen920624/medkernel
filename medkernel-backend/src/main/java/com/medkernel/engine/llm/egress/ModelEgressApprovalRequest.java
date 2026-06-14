package com.medkernel.engine.llm.egress;

import jakarta.validation.constraints.NotBlank;

/**
 * 高敏出域审批决定请求（LLM-03）。
 */
public record ModelEgressApprovalRequest(
    @NotBlank String capabilityCode,
    @NotBlank String payloadHash,
    @NotBlank String decision
) {}
