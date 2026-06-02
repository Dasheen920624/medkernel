package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 运行结果与历史版本重放绑定。
 */
@Table("mk_version_replay_binding")
public record VersionReplayBinding(
    @Id Long id,
    @Column("binding_id") String bindingId,
    @Column("tenant_id") String tenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("version_id") String versionId,
    @Column("patient_snapshot_id") String patientSnapshotId,
    @Column("runtime_event_id") String runtimeEventId,
    @Column("result_hash") String resultHash,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
