package com.medkernel.engine.cdss.risk;

import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * CDSS 风险矩阵单条规则变更请求。
 */
public record CdssRiskMatrixEntryRequest(
    @NotBlank String triggerPoint,
    @NotNull RecommendationRiskLevel severityLevel,
    @NotNull CdssAutomationLevel automationLevel,
    @NotNull RecommendationRiskLevel riskLevel,
    @NotNull CdssReviewRequirement reviewRequirement,
    @Min(0) int silentRunHours,
    @NotBlank String releaseGate,
    boolean autoExecutionAllowed,
    String samdClassification,
    String regulatoryEvidence,
    @NotBlank String explanation
) {}
