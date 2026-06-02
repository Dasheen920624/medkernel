package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 通用版本发布计划。
 */
@Table("mk_version_release_plan")
public record VersionReleasePlan(
    @Id Long id,
    @Column("plan_id") String planId,
    @Column("tenant_id") String tenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("version_id") String versionId,
    @Column("from_version_id") String fromVersionId,
    @Column("target_org_path") String targetOrgPath,
    @Column("applicable_scope") String applicableScope,
    @Column("scope_type") VersionReleaseScopeType scopeType,
    @Column("scope_value") String scopeValue,
    VersionReleaseStatus status,
    @Column("impact_digest") String impactDigest,
    @Column("review_conclusion") String reviewConclusion,
    @Column("evidence_summary") String evidenceSummary,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
