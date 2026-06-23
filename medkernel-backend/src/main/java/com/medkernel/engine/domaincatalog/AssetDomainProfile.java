package com.medkernel.engine.domaincatalog;

import java.time.Instant;

import com.medkernel.engine.versioning.VersionedAssetType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 稳定资产身份的唯一主领域归类。
 *
 * <p>归类绑定资产身份而非具体版本，调整归类不会产生新的医学内容版本。
 */
@Table("asset_domain_profile")
public record AssetDomainProfile(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("primary_domain_code") String primaryDomainCode,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
