package com.medkernel.engine.cdss.risk;

import com.medkernel.engine.recommendation.RecommendationRiskLevel;

/**
 * CDSS 风险矩阵评估结果，随推荐卡落库用于解释、门槛和合规预留追溯。
 */
public record CdssRiskAssessment(
    String riskMatrixId,
    String riskMatrixVersion,
    RecommendationRiskLevel riskLevel,
    CdssReviewRequirement reviewRequirement,
    int silentRunHours,
    String releaseGate,
    boolean autoExecutionAllowed,
    String samdClassification,
    String regulatoryEvidence,
    String explanation
) {
    public CdssRiskAssessment {
        if (riskLevel == null) {
            riskLevel = RecommendationRiskLevel.LOW;
        }
        if (reviewRequirement == null) {
            reviewRequirement = CdssReviewRequirement.OPTIONAL_REVIEW;
        }
        if (silentRunHours < 0) {
            silentRunHours = 0;
        }
        releaseGate = hasText(releaseGate) ? releaseGate.trim() : "STANDARD_CHANGE_REVIEW";
        samdClassification = hasText(samdClassification) ? samdClassification.trim() : "NMPA_RESERVED";
        regulatoryEvidence = hasText(regulatoryEvidence) ? regulatoryEvidence.trim() : "NOT_ASSESSED";
        explanation = hasText(explanation) ? explanation.trim() : "CDSS 风险矩阵默认基线";
    }

    public boolean requiresPhysicianConfirmation() {
        return riskLevel == RecommendationRiskLevel.HIGH
            || riskLevel == RecommendationRiskLevel.CRITICAL
            || reviewRequirement == CdssReviewRequirement.PHYSICIAN_CONFIRMATION;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
