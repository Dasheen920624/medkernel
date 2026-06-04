package com.medkernel.engine.recommendation;

import java.time.Instant;

/**
 * 推荐卡投影为统一协同待办的只读行。
 */
public record RecommendationWorkflowTodoRow(
    String cardId,
    RecommendationCardType cardType,
    String title,
    String summary,
    RecommendationRiskLevel riskLevel,
    RecommendationCardStatus status,
    Instant expiresAt,
    String traceId,
    Instant createdAt,
    String patientId,
    String encounterId,
    String triggerType,
    String scenarioCode
) {
}
