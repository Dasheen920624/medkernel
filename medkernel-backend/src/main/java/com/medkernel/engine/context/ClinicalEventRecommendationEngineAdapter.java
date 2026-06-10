package com.medkernel.engine.context;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.recommendation.RecommendationCard;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationEvaluationResponse;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;

/**
 * 临床事件到 CDSS 确定性评估入口的适配器。
 */
@Component
public class ClinicalEventRecommendationEngineAdapter implements ClinicalEventEngineAdapter {

    private final RecommendationEngineService recommendations;

    public ClinicalEventRecommendationEngineAdapter(RecommendationEngineService recommendations) {
        this.recommendations = recommendations;
    }

    @Override
    public ClinicalEventEngine engine() {
        return ClinicalEventEngine.CDSS;
    }

    @Override
    public ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context) {
        RecommendationEvaluationResponse response = recommendations.evaluate(new RecommendationTriggerRequest(
            triggerCode(context),
            context.triggerPoint(),
            context.eventId(),
            context.contextSnapshotId(),
            context.patientId(),
            context.encounterId(),
            patientPathwayId(context),
            context.triggerPoint(),
            context.packageVersion(),
            context.payloadDigest(),
            context.occurredAt(),
            List.of()
        ));
        return ClinicalEventEngineDispatchResult.dispatched(
            engine(), response.triggerId(), dispatchMessage(response));
    }

    private String triggerCode(ClinicalEventContext context) {
        return "CLINICAL_EVENT_" + context.eventType().name() + "_" + context.eventId();
    }

    private String dispatchMessage(RecommendationEvaluationResponse response) {
        String summaries = response.cards().stream()
            .map(RecommendationCard::sourceSummary)
            .filter(ClinicalEventRecommendationEngineAdapter::hasText)
            .limit(3)
            .collect(Collectors.joining(" | "));
        String base = "CDSS 推荐已完成确定性评估 cards=" + response.visibleCardCount();
        return hasText(summaries) ? base + " sources=" + summaries : base;
    }

    private String patientPathwayId(ClinicalEventContext context) {
        if (context == null || context.payload() == null || context.payload().isNull()) {
            return null;
        }
        String direct = text(context.payload().path("patientPathwayId"));
        if (hasText(direct)) {
            return direct;
        }
        return text(context.payload().path("eventPayload").path("patientPathwayId"));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
