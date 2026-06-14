package com.medkernel.engine.embed;

import jakarta.validation.constraints.NotBlank;

/**
 * 嵌入会话按一次性令牌读取临床建议的请求。
 */
public record EmbedRecommendationCardsRequest(
    @NotBlank String token
) {}
