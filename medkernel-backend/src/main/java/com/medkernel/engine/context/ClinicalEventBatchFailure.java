package com.medkernel.engine.context;

/**
 * 批量临床事件中单条失败明细。
 */
public record ClinicalEventBatchFailure(
    String eventId,
    String errorCode,
    String message,
    boolean retryable
) {}
