package com.medkernel.engine.authoring;

import java.time.Instant;

import com.medkernel.engine.versioning.VersionedAssetType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 统一创作资产编目资料。
 */
@Table("mk_engine_authoring_asset_profile")
public record AuthoringAssetProfile(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_id") String assetId,
    String category,
    @Column("tags_json") String tagsJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
