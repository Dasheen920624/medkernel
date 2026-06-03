package com.medkernel.engine.pkg;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 试点首发配置包模板资产项。
 */
@Table("mk_pkg_pilot_template_item")
public record PilotPackageTemplateItem(
    @Id Long id,
    @Column("item_id") String itemId,
    @Column("tenant_id") String tenantId,
    @Column("template_id") String templateId,
    @Column("asset_type") PackageItemAssetType assetType,
    @Column("asset_id") String assetId,
    @Column("asset_version") String assetVersion,
    @Column("required_flag") Boolean requiredFlag,
    @Column("sort_order") Integer sortOrder,
    @Column("dependency_note") String dependencyNote,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public boolean required() {
        return !Boolean.FALSE.equals(requiredFlag);
    }
}
