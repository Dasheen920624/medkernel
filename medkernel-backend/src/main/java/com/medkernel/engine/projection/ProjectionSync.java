package com.medkernel.engine.projection;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.clinical.model.ClinicalIds;

/**
 * 投影重建同步任务记录。
 */
@Table("mk_projection_sync")
public record ProjectionSync(
    @Id Long id,
    @Column("sync_id") String syncId,
    @Column("tenant_id") String tenantId,
    @Column("target_type") ProjectionTargetType targetType,
    @Column("status") ProjectionSyncStatus status,
    @Column("source_count") Integer sourceCount,
    @Column("projection_count") Integer projectionCount,
    @Column("source_hash") String sourceHash,
    @Column("projection_hash") String projectionHash,
    @Column("message") String message,
    @Column("started_at") Instant startedAt,
    @Column("finished_at") Instant finishedAt,
    @Column("requested_by") String requestedBy,
    @Column("trace_id") String traceId
) {
    public static ProjectionSync running(String tenantId, ProjectionTargetType targetType, String requestedBy,
            String traceId, Instant startedAt) {
        return new ProjectionSync(
            null,
            "ps-" + ClinicalIds.newUlid(),
            tenantId,
            targetType,
            ProjectionSyncStatus.RUNNING,
            0,
            0,
            null,
            null,
            "投影重建执行中",
            startedAt,
            null,
            requestedBy,
            traceId);
    }

    public ProjectionSync finish(ProjectionSyncStatus newStatus, int newSourceCount, int newProjectionCount,
            String newSourceHash, String newProjectionHash, String newMessage, Instant finishedAt) {
        return new ProjectionSync(
            id,
            syncId,
            tenantId,
            targetType,
            newStatus,
            newSourceCount,
            newProjectionCount,
            newSourceHash,
            newProjectionHash,
            newMessage,
            startedAt,
            finishedAt,
            requestedBy,
            traceId);
    }
}
