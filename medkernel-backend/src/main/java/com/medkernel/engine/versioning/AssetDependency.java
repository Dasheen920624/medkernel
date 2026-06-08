package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 资产版本依赖图边。
 */
@Table("mk_version_asset_dependency")
public record AssetDependency(
    @Id Long id,
    @Column("dependency_id") String dependencyId,
    @Column("tenant_id") String tenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("version_id") String versionId,
    @Column("depends_on_asset_type") VersionedAssetType dependsOnAssetType,
    @Column("depends_on_identity") String dependsOnIdentity,
    @Column("min_version_no") String minVersionNo,
    @Column("max_version_no") String maxVersionNo,
    @Column("dependency_kind") AssetDependencyKind dependencyKind,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
