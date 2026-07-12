package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 可移植资产版本的来源、许可、精确依赖、校验和合成测试不可变事实。 */
@Table("mk_version_asset_provenance")
public record PortableAssetProvenance(
    @Id Long id,
    @Column("provenance_id") String provenanceId,
    @Column("tenant_id") String tenantId,
    @Column("authority_id") String authorityId,
    @Column("delivery_id") String deliveryId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("version_id") String versionId,
    @Column("provenance_json") String provenanceJson,
    @Column("provenance_digest") String provenanceDigest,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
