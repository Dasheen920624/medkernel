package com.medkernel.engine.context;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 将同一个 {@link ClinicalEventContext} 按固定顺序派发到规则、路径和 CDSS。
 */
@Component
public class ClinicalEventEngineDispatcher {

    private final Map<ClinicalEventEngine, ClinicalEventEngineAdapter> adapters;

    public ClinicalEventEngineDispatcher(List<ClinicalEventEngineAdapter> adapters) {
        this.adapters = new EnumMap<>(ClinicalEventEngine.class);
        for (ClinicalEventEngineAdapter adapter : adapters == null ? List.<ClinicalEventEngineAdapter>of() : adapters) {
            this.adapters.put(adapter.engine(), adapter);
        }
    }

    public List<ClinicalEventEngineDispatchResult> dispatch(ClinicalEventContext context) {
        return ClinicalEventEngine.requiredEngines().stream()
            .map(engine -> adapter(engine).dispatch(context))
            .toList();
    }

    private ClinicalEventEngineAdapter adapter(ClinicalEventEngine engine) {
        ClinicalEventEngineAdapter adapter = adapters.get(engine);
        if (adapter == null) {
            throw new ApiException(ErrorCode.ENG_EVENT_005, "缺少临床事件引擎适配器: " + engine);
        }
        return adapter;
    }
}
