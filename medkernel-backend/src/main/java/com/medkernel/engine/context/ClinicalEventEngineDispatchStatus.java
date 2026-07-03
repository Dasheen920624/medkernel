package com.medkernel.engine.context;

/**
 * 临床事件上下文进入下游能力后的派发状态。
 */
public enum ClinicalEventEngineDispatchStatus {
    DISPATCHED,
    SKIPPED,
    UNAVAILABLE
}
