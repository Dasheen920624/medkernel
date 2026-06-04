package com.medkernel.engine.followup;

/**
 * 随访问卷下发 / 作答响应。
 */
public record FollowupQuestionnaireResponse(
    String questionnaireId,
    String taskId,
    String questionnaireTemplateId,
    String status,
    String traceId
) {}
