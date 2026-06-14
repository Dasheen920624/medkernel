package com.medkernel.engine.integration.masterdata;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 外部主数据记录到院内权威记录的版本映射。
 */
@Table("mk_integration_master_data_sync_record")
public record MasterDataSyncRecord(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("source_system") String sourceSystem,
    @Column("resource_type") MasterDataResourceType resourceType,
    @Column("source_record_id") String sourceRecordId,
    @Column("internal_id") String internalId,
    @Column("source_version") long sourceVersion,
    @Column("payload_hash") String payloadHash,
    @Column("status") MasterDataRecordStatus status,
    @Column("last_batch_id") String lastBatchId,
    @Column("source_updated_at") Instant sourceUpdatedAt,
    @Column("updated_at") Instant updatedAt
) {
}
