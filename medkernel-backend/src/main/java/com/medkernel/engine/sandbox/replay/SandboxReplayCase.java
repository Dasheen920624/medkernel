package com.medkernel.engine.sandbox.replay;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 当前机构持有的不可变历史重放清单；仅状态允许从 IMPORTED 变为 REVOKED。 */
@Table("mk_sandbox_replay_case")
public record SandboxReplayCase(
    @Id Long id,
    @Column("replay_case_id") String replayCaseId,
    @Column("sandbox_tenant_id") String sandboxTenantId,
    @Column("source_tenant_ref") String sourceTenantRef,
    @Column("source_event_ref") String sourceEventRef,
    @Column("source_trace_ref") String sourceTraceRef,
    @Column("source_context_ref") String sourceContextRef,
    @Column("context_snapshot_json") String contextSnapshotJson,
    @Column("context_snapshot_hash") String contextSnapshotHash,
    @Column("source_runtime_release_ref") String sourceRuntimeReleaseRef,
    @Column("source_runtime_revision_no") Long sourceRuntimeRevisionNo,
    @Column("occurred_at") Instant occurredAt,
    @Column("manifest_hash") String manifestHash,
    @Column("deidentification_profile") String deidentificationProfile,
    SandboxReplayStatus status,
    @Column("imported_at") Instant importedAt,
    @Column("imported_by") String importedBy,
    @Column("revoked_at") Instant revokedAt,
    @Column("revoked_by") String revokedBy,
    @Column("revoke_reason") String revokeReason,
    @Column("created_at") Instant createdAt,
    @Column("updated_at") Instant updatedAt,
    @Column("trace_id") String traceId
) {
    public SandboxReplayCase revoke(Instant at, String actor, String reason, String nextTraceId) {
        return new SandboxReplayCase(
            id, replayCaseId, sandboxTenantId, sourceTenantRef, sourceEventRef, sourceTraceRef,
            sourceContextRef, contextSnapshotJson, contextSnapshotHash,
            sourceRuntimeReleaseRef, sourceRuntimeRevisionNo,
            occurredAt, manifestHash, deidentificationProfile, SandboxReplayStatus.REVOKED,
            importedAt, importedBy, at, actor, reason, createdAt, at, nextTraceId);
    }
}
