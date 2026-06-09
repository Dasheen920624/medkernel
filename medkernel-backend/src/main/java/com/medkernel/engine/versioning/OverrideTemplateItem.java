package com.medkernel.engine.versioning;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 覆盖模板资产项。
 */
@Table("mk_version_override_template_item")
public record OverrideTemplateItem(
    @Id Long id,
    @Column("item_id") String itemId,
    @Column("template_id") String templateId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("inherited_version_id") String inheritedVersionId,
    @Column("source_override_version_id") String sourceOverrideVersionId,
    @Column("override_mode") InheritanceOverrideMode overrideMode,
    InheritancePropagation propagation,
    @Column("applicable_scope") String applicableScope,
    @Column("diff_summary") String diffSummary,
    @Column("override_reason") String overrideReason
) {
}
