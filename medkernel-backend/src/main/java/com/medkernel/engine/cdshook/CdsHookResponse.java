package com.medkernel.engine.cdshook;

import java.util.List;

import com.medkernel.engine.recommendation.RecommendationEvaluationResponse;

/**
 * OPT-02 CDS Hooks 风格触发响应契约。
 */
public record CdsHookResponse(
    String cdsHookVersion,
    List<CdsHookCard> cards,
    List<CdsHookSystemAction> systemActions
) {
    public CdsHookResponse {
        cdsHookVersion = CdsHookContract.normalizeVersion(cdsHookVersion);
        cards = cards == null ? List.of() : List.copyOf(cards);
        systemActions = systemActions == null ? List.of() : List.copyOf(systemActions);
    }

    public static CdsHookResponse fromRecommendationEvaluation(RecommendationEvaluationResponse response) {
        return new CdsHookResponse(
            CdsHookContract.CURRENT_VERSION,
            response.cards().stream()
                .map(CdsHookCard::fromRecommendationCard)
                .toList(),
            List.of()
        );
    }
}
