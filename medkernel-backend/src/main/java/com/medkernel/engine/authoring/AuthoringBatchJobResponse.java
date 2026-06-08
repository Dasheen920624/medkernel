package com.medkernel.engine.authoring;

import java.time.Instant;
import java.util.List;

/**
 * 创作批量任务响应。
 */
public record AuthoringBatchJobResponse(
    String jobId,
    AuthoringBatchJobType jobType,
    AuthoringBatchJobStatus status,
    int totalCount,
    int successCount,
    int failureCount,
    int retryableCount,
    String resultSummaryJson,
    List<AuthoringBatchItemResponse> items,
    String traceId,
    Instant createdAt,
    Instant updatedAt
) {
    public AuthoringBatchJobResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
