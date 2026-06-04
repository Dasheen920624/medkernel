package com.medkernel.engine.embed;

/**
 * 嵌入反馈回调受理响应。
 */
public record EmbedFeedbackResponse(
    String token,
    String actionType,
    EmbedConnectionStatus callbackStatus,
    boolean callbackDelivered,
    String degradationReason,
    String traceId
) {}
