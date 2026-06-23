package com.medkernel.engine.release;

import java.time.Instant;

import com.medkernel.engine.versioning.VersionedAssetType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 平台基线中的精确资产版本条目。
 */
@Table("platform_baseline_item")
public record PlatformBaselineItem(
    @Id Long id,
    @Column("baseline_release_id") String baselineReleaseId,
    @Column("source_tenant_id") String sourceTenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("entry_state") ReleaseEntryState entryState,
    @Column("version_id") String versionId,
    @Column("version_no") String versionNo,
    @Column("content_hash") String contentHash,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {
}
