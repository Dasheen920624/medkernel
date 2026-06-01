package com.medkernel.engine.context;

import java.util.List;

import org.springframework.stereotype.Component;

import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.recommendation.RecommendationTriggerResponse;

/**
 * 临床事件到 CDSS 推荐触发入口的适配器。
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
        RecommendationTriggerResponse response = recommendations.trigger(new RecommendationTriggerRequest(
            "CLINICAL_EVENT_" + context.eventType().name(),
            "CLINICAL_EVENT",
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
            engine(), response.triggerId(), "CDSS 推荐入口已接收临床事件上下文");
    }
}
