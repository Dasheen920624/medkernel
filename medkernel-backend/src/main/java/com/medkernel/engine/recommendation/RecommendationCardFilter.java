package com.medkernel.engine.recommendation;

/**
 * 推荐卡列表过滤条件：按状态、风险级别、场景代码、患者、就诊和触发点过滤；空字段表示不过滤。
 */
public record RecommendationCardFilter(
    RecommendationCardStatus status,
    RecommendationRiskLevel riskLevel,
    String scenarioCode,
    String patientId,
    String encounterId,
    String triggerPoint
) {
    public RecommendationCardFilter(
            RecommendationCardStatus status,
            RecommendationRiskLevel riskLevel,
            String scenarioCode,
            String patientId) {
        this(status, riskLevel, scenarioCode, patientId, null, null);
    }
}
