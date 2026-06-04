package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 组织继承局部覆盖解释。
 */
@Table("mk_version_inheritance_override")
public record InheritanceOverride(
    @Id Long id,
    @Column("override_id") String overrideId,
    @Column("tenant_id") String tenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("inherited_version_id") String inheritedVersionId,
    @Column("override_version_id") String overrideVersionId,
    @Column("override_mode") InheritanceOverrideMode overrideMode,
    @Column("propagation") InheritancePropagation propagation,
    @Column("org_path") String orgPath,
    @Column("applicable_scope") String applicableScope,
    @Column("diff_summary") String diffSummary,
    @Column("override_reason") String overrideReason,
    @Column("impact_scope") String impactScope,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
