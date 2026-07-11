package com.medkernel.engine.recommendation;

import java.util.List;

/**
 * 推荐触发出参：返回 triggerId、触发状态、本次落库的推荐卡数量、卡片身份和 traceId，字段语义见 API spec。
 */
public record RecommendationTriggerResponse(
    String triggerId,
    RecommendationTriggerStatus status,
    int cardCount,
    List<String> cardIds,
    String traceId
) {
    public RecommendationTriggerResponse(
            String triggerId,
            RecommendationTriggerStatus status,
            int cardCount,
            String traceId) {
        this(triggerId, status, cardCount, List.of(), traceId);
    }

    public RecommendationTriggerResponse {
        cardIds = cardIds == null ? List.of() : List.copyOf(cardIds);
    }
}
