package com.medkernel.engine.recommendation;

import java.time.Instant;

import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;

/**
 * 临床提醒卡聚合出参：把推荐卡事实与触发上下文合并，供医生端页面直接消费。
 */
public record RecommendationClinicalCardResponse(
    String cardId,
    String triggerId,
    String patientId,
    String encounterId,
    String patientPathwayId,
    String scenarioCode,
    String triggerType,
    String contextSnapshotId,
    String packageVersion,
    Instant occurredAt,
    String cardCode,
    RecommendationCardType cardType,
    String title,
    String summary,
    String suggestedAction,
    RecommendationRiskLevel riskLevel,
    RecommendationInterruptLevel interruptLevel,
    RecommendationCardStatus status,
    boolean requiresPhysicianConfirmation,
    boolean aiGenerated,
    String sourceSummary,
    String explanationJson,
    String fatigueKey,
    Instant expiresAt,
    Instant createdAt,
    String createdBy,
    String traceId,
    String riskMatrixId,
    String riskMatrixVersion,
    CdssAutomationLevel automationLevel,
    CdssReviewRequirement reviewRequirement,
    int silentRunHours,
    String releaseGate,
    boolean autoExecutionAllowed,
    String samdClassification,
    String regulatoryEvidence,
    String riskMatrixExplanation
) {

    public static RecommendationClinicalCardResponse from(
            RecommendationCard card,
            RecommendationTrigger trigger) {
        return new RecommendationClinicalCardResponse(
            card.cardId(),
            card.triggerId(),
            trigger.patientId(),
            trigger.encounterId(),
            trigger.patientPathwayId(),
            trigger.scenarioCode(),
            trigger.triggerType(),
            trigger.contextSnapshotId(),
            trigger.packageVersion(),
            trigger.occurredAt(),
            card.cardCode(),
            card.cardType(),
            card.title(),
            card.summary(),
            card.suggestedAction(),
            card.riskLevel(),
            card.interruptLevel(),
            card.status(),
            card.requiresPhysicianConfirmation(),
            card.aiGenerated(),
            card.sourceSummary(),
            card.explanationJson(),
            card.fatigueKey(),
            card.expiresAt(),
            card.createdAt(),
            card.createdBy(),
            card.traceId(),
            card.riskMatrixId(),
            card.riskMatrixVersion(),
            card.automationLevel(),
            card.reviewRequirement(),
            card.silentRunHours(),
            card.releaseGate(),
            card.autoExecutionAllowed(),
            card.samdClassification(),
            card.regulatoryEvidence(),
            card.riskMatrixExplanation());
    }
}
