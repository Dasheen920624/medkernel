package com.medkernel.engine.embed;

import com.medkernel.engine.recommendation.RecommendationCardStatus;
import com.medkernel.engine.recommendation.RecommendationClinicalCardResponse;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;

/**
 * 嵌入页面可见的最小临床建议视图，不暴露内部租户字段和原始解释载荷。
 */
public record EmbedRecommendationCardResponse(
    String cardId,
    String title,
    String summary,
    String suggestedAction,
    RecommendationRiskLevel riskLevel,
    RecommendationInterruptLevel interruptLevel,
    RecommendationCardStatus status,
    boolean requiresPhysicianConfirmation,
    boolean aiGenerated,
    String sourceSummary,
    String traceId
) {
    static EmbedRecommendationCardResponse from(RecommendationClinicalCardResponse card) {
        return new EmbedRecommendationCardResponse(
            card.cardId(),
            card.title(),
            card.summary(),
            card.suggestedAction(),
            card.riskLevel(),
            card.interruptLevel(),
            card.status(),
            card.requiresPhysicianConfirmation(),
            card.aiGenerated(),
            card.sourceSummary(),
            card.traceId());
    }
}
