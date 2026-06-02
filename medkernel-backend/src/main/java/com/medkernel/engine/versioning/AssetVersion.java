package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 通用配置资产版本实体。
 *
 * <p>物理表按迁移规约命名为 {@code mk_version_asset_version}；领域对象保持
 * {@code AssetVersion}，供知识、术语、规则、路径、包、评估等配置资产复用。
 */
@Table("mk_version_asset_version")
public record AssetVersion(
    @Id Long id,
    @Column("version_id") String versionId,
    @Column("tenant_id") String tenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("version_no") String versionNo,
    @Column("org_path") String organizationScope,
    @Column("applicable_scope") String applicableScope,
    @Column("content_hash") String contentHash,
    AssetVersionStatus status,
    @Column("active_scope_key") String activeScopeKey,
    @Column("source_ref") String sourceRef,
    @Column("effective_from") Instant effectiveFrom,
    @Column("effective_to") Instant effectiveTo,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {

    public AssetVersion withVersionId(String newVersionId) {
        return new AssetVersion(
            id, newVersionId, tenantId, assetType, assetIdentity, versionNo,
            organizationScope, applicableScope, contentHash, status, activeScopeKey, sourceRef,
            effectiveFrom, effectiveTo, createdAt, createdBy, updatedAt, updatedBy, traceId
        );
    }

    public AssetVersion withContentHash(String newContentHash, Instant now, String actor) {
        return new AssetVersion(
            id, versionId, tenantId, assetType, assetIdentity, versionNo,
            organizationScope, applicableScope, newContentHash, status, activeScopeKey, sourceRef,
            effectiveFrom, effectiveTo, createdAt, createdBy, now, actor, traceId
        );
    }

    public AssetVersion withStatus(AssetVersionStatus newStatus, String newActiveScopeKey, Instant now, String actor) {
        return new AssetVersion(
            id, versionId, tenantId, assetType, assetIdentity, versionNo,
            organizationScope, applicableScope, contentHash, newStatus, newActiveScopeKey, sourceRef,
            effectiveFrom, effectiveTo, createdAt, createdBy, now, actor, traceId
        );
    }
}
