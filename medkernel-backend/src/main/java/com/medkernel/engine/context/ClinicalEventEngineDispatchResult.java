package com.medkernel.engine.context;

/**
 * 单个下游引擎接收临床事件上下文后的结果摘要。
 */
public record ClinicalEventEngineDispatchResult(
    ClinicalEventEngine engine,
    ClinicalEventEngineDispatchStatus status,
    String downstreamReferenceId,
    String message
) {
    public static ClinicalEventEngineDispatchResult dispatched(
            ClinicalEventEngine engine, String downstreamReferenceId, String message) {
        return new ClinicalEventEngineDispatchResult(
            engine, ClinicalEventEngineDispatchStatus.DISPATCHED, downstreamReferenceId, message);
    }

    public static ClinicalEventEngineDispatchResult skipped(
            ClinicalEventEngine engine, String downstreamReferenceId, String message) {
        return new ClinicalEventEngineDispatchResult(
            engine, ClinicalEventEngineDispatchStatus.SKIPPED, downstreamReferenceId, message);
    }

    public static ClinicalEventEngineDispatchResult unavailable(
            ClinicalEventEngine engine, String downstreamReferenceId, String message) {
        return new ClinicalEventEngineDispatchResult(
            engine, ClinicalEventEngineDispatchStatus.UNAVAILABLE, downstreamReferenceId, message);
    }
}
