package com.medkernel.engine.safety;

import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 临床安全红线草稿创建请求。
 *
 * <p>本入口只登记真实红线草稿，不创建统一 {@code SAFETY} 资产；资产版本必须由静默试运行达标后的上线流程生成。
 */
public record ClinicalRedlineDraftRequest(
    @NotBlank @Size(max = 64) String redlineId,
    @NotNull ClinicalRedlineCategory category,
    @NotBlank @Size(max = 64) String triggerPoint,
    @NotBlank @Size(max = 32) String scopeType,
    @NotBlank @Size(max = 128) String scopeRef,
    @NotBlank @Size(max = 128) String redlineKey,
    @NotBlank @Size(max = 64) String redlineVersion,
    @NotNull RecommendationRiskLevel hazardSeverity,
    @NotBlank @Size(max = 128) String riskMatrixId,
    @NotBlank @Size(max = 64) String riskMatrixVersion,
    @NotNull CdssReviewRequirement reviewRequirement,
    @Min(0) int silentRunHours,
    @NotBlank @Size(max = 128) String releaseGate,
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 2000) String clinicalHazard,
    @NotBlank String conditionDsl,
    @NotBlank @Size(max = 1000) String evidenceSource,
    @NotBlank @Size(max = 1000) String evidenceReference,
    Long sourceVersionId,
    boolean lowerTenantOverrideAllowed
) {
}
