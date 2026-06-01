package com.medkernel.engine.projection;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 图投影快照行，作为可重建投影的派生副本。
 */
@Table("mk_projection_snapshot")
public record ProjectionSnapshot(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("target_type") ProjectionTargetType targetType,
    @Column("fact_key") String factKey,
    @Column("fact_kind") ProjectionFactKind factKind,
    @Column("object_type") String objectType,
    @Column("object_id") String objectId,
    @Column("subject_key") String subjectKey,
    @Column("predicate_name") String predicate,
    @Column("object_key") String objectKey,
    @Column("content_hash") String contentHash,
    @Column("canonical_payload") String canonicalPayload,
    @Column("source_updated_at") Instant sourceUpdatedAt,
    @Column("synced_at") Instant syncedAt,
    @Column("trace_id") String traceId
) {
    public static ProjectionSnapshot fromFact(String tenantId, ProjectionFact fact, Instant syncedAt, String traceId) {
        return new ProjectionSnapshot(
            null,
            tenantId,
            fact.targetType(),
            fact.factKey(),
            fact.factKind(),
            fact.objectType(),
            fact.objectId(),
            fact.subjectKey(),
            fact.predicate(),
            fact.objectKey(),
            fact.contentHash(),
            fact.canonicalPayload(),
            fact.sourceUpdatedAt(),
            syncedAt,
            traceId);
    }
}
