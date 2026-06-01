package com.medkernel.engine.context;

import org.springframework.stereotype.Component;

import com.medkernel.engine.pathway.PathwayEngineService;
import com.medkernel.engine.pathway.PathwayEventDispatchResponse;

/**
 * 临床事件到路径引擎上下文入口的适配器。
 */
@Component
public class ClinicalEventPathwayEngineAdapter implements ClinicalEventEngineAdapter {

    private final PathwayEngineService pathways;

    public ClinicalEventPathwayEngineAdapter(PathwayEngineService pathways) {
        this.pathways = pathways;
    }

    @Override
    public ClinicalEventEngine engine() {
        return ClinicalEventEngine.PATHWAY;
    }

    @Override
    public ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context) {
        PathwayEventDispatchResponse response = pathways.dispatchClinicalEvent(context);
        return ClinicalEventEngineDispatchResult.dispatched(
            engine(), response.eventId(), "路径引擎已接收临床事件上下文");
    }
}
