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
    @Column("electronic_signature_id") String electronicSignatureId,
    @Column("electronic_signature_subject") String electronicSignatureSubject,
    @Column("electronic_signature_hash") String electronicSignatureHash,
    @Column("electronic_signature_signed_at") Instant electronicSignatureSignedAt,
    @Column("quality_gate_summary") String qualityGateSummary,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public VersionReleasePlan(
            Long id,
            String planId,
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String versionId,
            String fromVersionId,
            String targetOrgPath,
            String applicableScope,
            VersionReleaseScopeType scopeType,
            String scopeValue,
            VersionReleaseStatus status,
            String impactDigest,
            String reviewConclusion,
            String evidenceSummary,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(
            id,
            planId,
            tenantId,
            assetType,
            assetIdentity,
            versionId,
            fromVersionId,
            targetOrgPath,
            applicableScope,
            scopeType,
            scopeValue,
            status,
            impactDigest,
            reviewConclusion,
            evidenceSummary,
            null,
            null,
            null,
            null,
            null,
            createdAt,
            createdBy,
            updatedAt,
            updatedBy,
            traceId
        );
    }
}
