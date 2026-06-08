package com.medkernel.engine.context;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

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
            "CLINICAL_EVENT_" + context.eventType().name(),
            context.triggerPoint(),
            context.eventId(),
            context.contextSnapshotId(),
            context.patientId(),
            context.encounterId(),
            null,
            context.triggerPoint(),
            context.packageVersion(),
            context.payloadDigest(),
            context.occurredAt(),
            List.of()
        ));
        return ClinicalEventEngineDispatchResult.dispatched(
            engine(), response.triggerId(), dispatchMessage(response));
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
