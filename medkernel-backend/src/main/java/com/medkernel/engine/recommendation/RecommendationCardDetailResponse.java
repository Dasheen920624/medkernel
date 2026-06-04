package com.medkernel.engine.recommendation;

import java.util.List;

/**
 * 推荐卡详情出参：聚合推荐卡 + 来源解释 + 反馈记录 + 疲劳治理信号，字段语义见 API spec。
 */
public record RecommendationCardDetailResponse(
    RecommendationCard card,
    RecommendationTrigger trigger,
    List<RecommendationSource> sources,
    List<RecommendationFeedback> feedback,
    List<RecommendationFatigueSignal> fatigueSignals
) {
    public RecommendationCardDetailResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
        feedback = feedback == null ? List.of() : List.copyOf(feedback);
        fatigueSignals = fatigueSignals == null ? List.of() : List.copyOf(fatigueSignals);
    }
}
