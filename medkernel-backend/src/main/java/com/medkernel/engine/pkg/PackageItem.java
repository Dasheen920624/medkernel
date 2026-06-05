package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.VersionedAssetType;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 包内资产条目实体。
 *
 * <p>锁定包包含的具体资产、类型及其确切版本。
 */
@Table("package_item")
public record PackageItem(
    @Id Long id,
    @Column("item_id") String itemId,
    @Column("tenant_id") String tenantId,
    @Column("package_id") String packageId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_id") String assetId,
    @Column("asset_version") String assetVersion,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
