package com.medkernel.engine.embed;

import jakarta.validation.constraints.NotBlank;

/**
 * 医生嵌入组件交互采纳/拒绝反馈数据契约 (GA-ENG-API-11)。
 */
public record EmbedFeedbackRequest(
    @NotBlank String token,
    @NotBlank String actionType, // ADOPT / REJECT
    String reason
) {}
