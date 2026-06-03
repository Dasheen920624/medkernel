package com.medkernel.engine.recommendation;

import java.util.List;

/**
 * 推荐评估响应：返回触发结果、可展示卡、疲劳抑制数量和模型降级状态。
 */
public record RecommendationEvaluationResponse(
    String triggerId,
    RecommendationTriggerStatus status,
    int totalCardCount,
    int visibleCardCount,
    int suppressedCardCount,
    RecommendationModelStatus modelStatus,
    List<RecommendationCard> cards,
    String traceId
) {
    public RecommendationEvaluationResponse {
        cards = cards == null ? List.of() : List.copyOf(cards);
    }
}
