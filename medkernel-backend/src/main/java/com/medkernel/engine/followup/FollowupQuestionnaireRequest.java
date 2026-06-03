package com.medkernel.engine.followup;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

/**
 * 顶层随访问卷下发 / 作答请求。
 */
public record FollowupQuestionnaireRequest(
    @NotBlank String taskId,
    @NotBlank String questionnaireTemplateId,
    @NotBlank String formData,
    String answerData,
    BigDecimal score,
    @NotBlank String idempotencyKey,
    String executorId,
    String executorType
) {}
