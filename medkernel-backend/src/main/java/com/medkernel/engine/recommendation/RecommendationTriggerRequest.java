package com.medkernel.engine.recommendation;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 推荐触发入参：triggerCode / triggerType / scenarioCode / inputDigest 必填，
 * 可携带候选 {@link RecommendationCardRequest} 列表（首版允许上游直接提交候选卡）。
 *
 * <p>fatigueSuppressionThreshold / fatigueWindowHours 为调用方传入的疲劳抑制策略；
 * 未传时只采集信号不自动抑制。modelEnhancementEnabled 是预留挂点，当前无真实模型网关时仍按
 * {@link RecommendationModelStatus#MODEL_DISABLED} 降级。
 */
public record RecommendationTriggerRequest(
    @NotBlank String triggerCode,
    @NotBlank String triggerType,
    String sourceEventId,
    String contextSnapshotId,
    String patientId,
    String encounterId,
    String patientPathwayId,
    @NotBlank String scenarioCode,
    String packageVersion,
    @NotBlank String inputDigest,
    Instant occurredAt,
    @Valid List<RecommendationCardRequest> candidateCards,
    Integer fatigueSuppressionThreshold,
    Integer fatigueWindowHours,
    Boolean modelEnhancementEnabled
) {
    public RecommendationTriggerRequest(
            String triggerCode,
            String triggerType,
            String sourceEventId,
            String contextSnapshotId,
            String patientId,
            String encounterId,
            String patientPathwayId,
            String scenarioCode,
            String packageVersion,
            String inputDigest,
            Instant occurredAt,
            List<RecommendationCardRequest> candidateCards) {
        this(triggerCode, triggerType, sourceEventId, contextSnapshotId, patientId, encounterId,
            patientPathwayId, scenarioCode, packageVersion, inputDigest, occurredAt, candidateCards,
            null, null, Boolean.FALSE);
    }

    public RecommendationTriggerRequest {
        candidateCards = candidateCards == null ? List.of() : List.copyOf(candidateCards);
    }
}
