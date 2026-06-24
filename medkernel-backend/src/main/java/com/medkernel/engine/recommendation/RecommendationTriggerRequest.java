package com.medkernel.engine.recommendation;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.cdshook.CdsHookContract;
import com.medkernel.engine.cdshook.CdsHookRequest;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 推荐触发入参：triggerCode / triggerType / contextSnapshotId / scenarioCode 必填，
 * 可携带候选 {@link RecommendationCardRequest} 列表；评估接口会先基于标准上下文与已发布资产
 * 生成确定性候选，再合并调用方提交的非 AI 候选卡。
 *
 * <p>疲劳抑制只读取配置中心 {@code medkernel.cdss.fatigue.policy}；缺配置时只采集信号不自动抑制。
 * modelEnhancementEnabled 是预留挂点，当前无真实模型网关时仍按
 * {@link RecommendationModelStatus#MODEL_DISABLED} 降级。
 */
public record RecommendationTriggerRequest(
    @NotBlank String triggerCode,
    @NotBlank String triggerType,
    String sourceEventId,
    @NotBlank String contextSnapshotId,
    String patientId,
    String encounterId,
    String patientPathwayId,
    @NotBlank String scenarioCode,
    String inputDigest,
    Instant occurredAt,
    @Valid List<RecommendationCardRequest> candidateCards,
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
            String inputDigest,
            Instant occurredAt,
            List<RecommendationCardRequest> candidateCards) {
        this(triggerCode, triggerType, sourceEventId, contextSnapshotId, patientId, encounterId,
            patientPathwayId, scenarioCode, inputDigest, occurredAt, candidateCards,
            Boolean.FALSE);
    }

    public RecommendationTriggerRequest {
        candidateCards = candidateCards == null ? List.of() : List.copyOf(candidateCards);
    }

    public ClinicalEventTriggerPoint cdsHook() {
        return CdsHookContract.requireSupportedHook(triggerType);
    }

    public CdsHookRequest toCdsHookRequest() {
        ObjectNode context = JsonNodeFactory.instance.objectNode();
        putIfPresent(context, "triggerCode", triggerCode);
        putIfPresent(context, "sourceEventId", sourceEventId);
        putIfPresent(context, "contextSnapshotId", contextSnapshotId);
        putIfPresent(context, "patientPathwayId", patientPathwayId);
        putIfPresent(context, "scenarioCode", scenarioCode);
        putIfPresent(context, "inputDigest", inputDigest);
        return new CdsHookRequest(
            cdsHook(),
            triggerCode,
            patientId,
            encounterId,
            null,
            context,
            null,
            null);
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }
}
