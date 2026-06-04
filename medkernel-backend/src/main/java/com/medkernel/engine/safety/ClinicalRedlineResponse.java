package com.medkernel.engine.safety;

import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;

/**
 * 临床安全红线目录响应项。
 */
public record ClinicalRedlineResponse(
    String redlineId,
    ClinicalRedlineCategory category,
    String redlineKey,
    String redlineVersion,
    ClinicalRedlineStatus status,
    String title,
    String clinicalHazard,
    String conditionDsl,
    RecommendationRiskLevel hazardSeverity,
    String riskMatrixId,
    String riskMatrixVersion,
    CdssReviewRequirement reviewRequirement,
    int silentRunHours,
    String releaseGate,
    String evidenceSource,
    String evidenceReference,
    Long sourceVersionId,
    boolean lowerTenantOverrideAllowed
) {
}
