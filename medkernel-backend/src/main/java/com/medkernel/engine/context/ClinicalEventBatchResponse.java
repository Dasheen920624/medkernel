package com.medkernel.engine.context;

import java.util.List;

/**
 * 临床事件批量受理结果。
 */
public record ClinicalEventBatchResponse(
    String batchId,
    List<ClinicalEventAcceptedResponse> items,
    List<ClinicalEventBatchFailure> failures,
    int totalCount,
    int acceptedCount,
    int failureCount,
    ClinicalEventBatchStatus status,
    String traceId
) {
    public ClinicalEventBatchResponse {
        items = items == null ? List.of() : List.copyOf(items);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
