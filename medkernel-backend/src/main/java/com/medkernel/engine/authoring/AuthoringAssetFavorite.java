package com.medkernel.engine.authoring;

import java.time.Instant;

import com.medkernel.engine.versioning.VersionedAssetType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 统一创作资产收藏关系。
 */
@Table("mk_engine_authoring_asset_favorite")
public record AuthoringAssetFavorite(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("user_id") String userId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_id") String assetId,
    @Column("created_at") Instant createdAt,
    @Column("trace_id") String traceId
) {}
