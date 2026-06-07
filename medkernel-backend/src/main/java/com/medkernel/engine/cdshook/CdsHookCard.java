package com.medkernel.engine.cdshook;

import java.util.List;

import com.medkernel.engine.recommendation.RecommendationCard;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;

/**
 * CDS Hooks 风格响应卡，承接 D3 推荐卡的客户面结构。
 */
public record CdsHookCard(
    String uuid,
    String summary,
    String detail,
    String indicator,
    CdsHookSource source,
    List<CdsHookSuggestion> suggestions,
    List<String> overrideReasons,
    boolean requiresPhysicianConfirmation
) {
    public CdsHookCard {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        overrideReasons = overrideReasons == null ? List.of() : List.copyOf(overrideReasons);
    }

    public static CdsHookCard fromRecommendationCard(RecommendationCard card) {
        return new CdsHookCard(
            card.cardId(),
            card.title(),
            card.summary(),
            indicator(card.riskLevel()),
            new CdsHookSource(card.sourceSummary(), null, null),
            List.of(),
            List.of(),
            card.requiresPhysicianConfirmation()
        );
    }

    private static String indicator(RecommendationRiskLevel riskLevel) {
        if (riskLevel == RecommendationRiskLevel.CRITICAL) {
            return "critical";
        }
        if (riskLevel == RecommendationRiskLevel.HIGH || riskLevel == RecommendationRiskLevel.MEDIUM) {
            return "warning";
        }
        return "info";
    }
}
