package com.medkernel.engine.embed;

import java.util.List;

/**
 * 嵌入会话临床建议列表响应。
 */
public record EmbedRecommendationCardsResponse(
    List<EmbedRecommendationCardResponse> items,
    String traceId
) {
    public EmbedRecommendationCardsResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
