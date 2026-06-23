package com.medkernel.engine.context;

import java.time.Instant;

import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.VersionedAssetType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 医院运行修订中的精确资产版本条目。
 */
@Table("clinical_runtime_release_item")
public record ClinicalRuntimeReleaseItem(
    @Id Long id,
    @Column("release_id") String releaseId,
    @Column("source_tenant_id") String sourceTenantId,
    @Column("source_layer") ReleaseSourceLayer sourceLayer,
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
