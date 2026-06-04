package com.medkernel.engine.recommendation;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 推荐卡候选入参（随触发请求一并提交）。字段含义见
 * {@link RecommendationCard}；至少含一条 {@link RecommendationSourceRequest} 来源，
 * 高风险卡必须 {@code requiresPhysicianConfirmation=true}，由
 * {@link RecommendationEngineService#trigger} 强制校验。
 */
public record RecommendationCardRequest(
    @NotBlank String cardCode,
    @NotNull RecommendationCardType cardType,
    @NotBlank String title,
    @NotBlank String summary,
    @NotBlank String suggestedAction,
    @NotNull RecommendationRiskLevel riskLevel,
    @NotNull RecommendationInterruptLevel interruptLevel,
    boolean requiresPhysicianConfirmation,
    boolean aiGenerated,
    @NotBlank String sourceSummary,
    String explanationJson,
    String fatigueKey,
    Instant expiresAt,
    CdssAutomationLevel automationLevel,
    @Valid List<RecommendationSourceRequest> sources
) {
    public RecommendationCardRequest {
        automationLevel = automationLevel == null
            ? CdssAutomationLevel.fromInterruptLevel(interruptLevel)
            : automationLevel;
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
