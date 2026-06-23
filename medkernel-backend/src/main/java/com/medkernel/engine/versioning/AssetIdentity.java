package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 可发布配置资产的跨版本稳定身份。
 *
 * <p>领域归类、内容版本和运行发布均引用该身份；版本号由服务端按
 * {@code latestVersionSequence} 单调分配。
 */
@Table("asset_identity")
public record AssetIdentity(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    AssetIdentityStatus status,
    @Column("latest_version_sequence") Long latestVersionSequence,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
