package com.medkernel.engine.integration.masterdata;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 主数据同步批次去敏台账，不保存原始人员或字典载荷。
 */
@Table("mk_integration_master_data_sync_batch")
public record MasterDataSyncBatch(
    @Id Long id,
    @Column("batch_id") String batchId,
    @Column("tenant_id") String tenantId,
    @Column("webhook_id") String webhookId,
    @Column("adapter_id") String adapterId,
    @Column("source_system") String sourceSystem,
    @Column("sync_mode") MasterDataSyncMode mode,
    @Column("previous_cursor") String previousCursor,
    @Column("cursor_value") String cursor,
    @Column("payload_hash") String payloadHash,
    @Column("status") MasterDataSyncStatus status,
    @Column("total_count") int totalCount,
    @Column("applied_count") int appliedCount,
    @Column("failed_count") int failedCount,
    @Column("error_summary") String errorSummary,
    @Column("created_at") Instant createdAt,
    @Column("processed_at") Instant processedAt,
    @Column("trace_id") String traceId
) {
}
