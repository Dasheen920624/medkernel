package com.medkernel.engine.integration.masterdata;

import java.time.Instant;
import java.util.List;

/**
 * 主数据同步结果，只返回来源记录标识和处理状态，不回显人员敏感字段。
 */
public record MasterDataSyncResponse(
    String batchId,
    String sourceSystem,
    String cursor,
    MasterDataSyncStatus status,
    int totalCount,
    int appliedCount,
    int failedCount,
    boolean idempotentReplay,
    Instant processedAt,
    String traceId,
    List<ItemResult> items
) {
    public MasterDataSyncResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record ItemResult(
        String recordId,
        MasterDataResourceType resourceType,
        MasterDataOperation operation,
        long sourceVersion,
        String internalId,
        MasterDataRecordStatus status
    ) {
    }
}
