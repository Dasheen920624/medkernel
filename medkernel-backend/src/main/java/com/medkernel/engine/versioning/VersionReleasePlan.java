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
    @Column("rollout_strategy") RolloutStrategy rolloutStrategy,
    @Column("rollout_config_json") String rolloutConfigJson,
    @Column("rollout_stage_index") Integer rolloutStageIndex,
    @Column("rollout_paused_reason") String rolloutPausedReason,
    VersionReleaseStatus status,
    @Column("impact_digest") String impactDigest,
    @Column("review_conclusion") String reviewConclusion,
    @Column("evidence_summary") String evidenceSummary,
    @Column("quality_gate_summary") String qualityGateSummary,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public VersionReleasePlan withRolloutState(
            VersionReleaseStatus rolloutStatus,
            int stageIndex,
            String pausedReason,
            Instant now,
            String actor,
            String traceId) {
        return new VersionReleasePlan(
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
            rolloutStrategy,
            rolloutConfigJson,
            stageIndex,
            pausedReason,
            rolloutStatus,
            impactDigest,
            reviewConclusion,
            evidenceSummary,
            qualityGateSummary,
            createdAt,
            createdBy,
            now,
            actor,
            traceId
        );
    }

    public VersionReleasePlan withRolloutRollback(
            String reason,
            Instant now,
            String actor,
            String traceId) {
        String rollbackAction = fromVersionId == null || fromVersionId.isBlank()
            ? "停止本次灰度，无上一钉点"
            : "恢复上一钉点 " + fromVersionId;
        String rollbackEvidence = "ROLLBACK 灰度回退：" + rollbackAction + "；原因：" + reason;
        String evidence = evidenceSummary == null || evidenceSummary.isBlank()
            ? rollbackEvidence
            : evidenceSummary + "；" + rollbackEvidence;
        return new VersionReleasePlan(
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
            rolloutStrategy,
            rolloutConfigJson,
            rolloutStageIndex,
            null,
            VersionReleaseStatus.ROLLED_BACK,
            impactDigest,
            reviewConclusion,
            evidence,
            qualityGateSummary,
            createdAt,
            createdBy,
            now,
            actor,
            traceId
        );
    }

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
            RolloutStrategy.ALL,
            null,
            0,
            null,
            status,
            impactDigest,
            reviewConclusion,
            evidenceSummary,
            null,
            createdAt,
            createdBy,
            updatedAt,
            updatedBy,
            traceId
        );
    }
}
