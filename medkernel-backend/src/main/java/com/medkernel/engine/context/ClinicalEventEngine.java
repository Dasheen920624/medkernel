package com.medkernel.engine.context;

import java.util.List;

/**
 * 临床事件上下文需要同步触达的确定性能力入口。
 */
public enum ClinicalEventEngine {
    RULE,
    PATHWAY,
    CDSS;

    public static List<ClinicalEventEngine> requiredEngines() {
        return List.of(RULE, PATHWAY, CDSS);
    }
}
