package com.medkernel.engine.followup;

import jakarta.validation.constraints.NotBlank;

/**
 * 随访结果回流请求。
 */
public record FollowupResultBackflowRequest(
    @NotBlank String planId,
    @NotBlank String taskId,
    @NotBlank String questionnaireId,
    @NotBlank String resultPayload,
    String abnormalFlag,
    @NotBlank String idempotencyKey
) {}
