package com.medkernel.engine.integration.masterdata;

import java.time.Instant;
import java.util.List;

/**
 * 院内主数据对账响应。
 *
 * <p>返回来源系统最近成功批次、增量游标、同步时间及各资源类型启停数量。
 */
public record MasterDataReconciliationResponse(
    String sourceSystem,
    String lastSuccessfulBatchId,
    String cursor,
    Instant lastSyncedAt,
    List<ResourceCount> resources
) {
    public MasterDataReconciliationResponse {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }

    /**
     * 单个主数据资源类型的启用与停用数量。
     */
    public record ResourceCount(
        MasterDataResourceType resourceType,
        long activeCount,
        long disabledCount
    ) {
    }
}
