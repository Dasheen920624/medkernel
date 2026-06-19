package com.medkernel.engine.sandbox.replay;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;

/** 历史重放清单内的精确资产内容快照，禁止映射为当前激活资产。 */
@Table("mk_sandbox_replay_asset_binding")
public record SandboxReplayAssetBinding(
    @Id Long id,
    @Column("binding_id") String bindingId,
    @Column("sandbox_tenant_id") String sandboxTenantId,
    @Column("replay_case_id") String replayCaseId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("version_id") String versionId,
    @Column("asset_version") String assetVersion,
    @Column("source_tier") SourceTier sourceTier,
    @Column("source_org_ref") String sourceOrgRef,
    @Column("content_json") String contentJson,
    @Column("content_hash") String contentHash,
    @Column("historical_status") AssetVersionStatus historicalStatus,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {
}
