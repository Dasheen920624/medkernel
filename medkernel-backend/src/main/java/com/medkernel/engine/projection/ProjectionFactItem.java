package com.medkernel.engine.projection;

import java.time.Instant;

/**
 * 投影事实列表项。
 *
 * <p>仅暴露可审计索引与摘要字段，不把图数据库或 Dify 作为业务权威源。
 */
public record ProjectionFactItem(
    String factKey,
    ProjectionFactKind factKind,
    String objectType,
    String objectId,
    String subjectKey,
    String predicate,
    String objectKey,
    String contentHash,
    Instant sourceUpdatedAt,
    Instant syncedAt,
    String traceId
) {
    public static ProjectionFactItem fromSnapshot(ProjectionSnapshot snapshot) {
        return new ProjectionFactItem(
            snapshot.factKey(),
            snapshot.factKind(),
            snapshot.objectType(),
            snapshot.objectId(),
            snapshot.subjectKey(),
            snapshot.predicate(),
            snapshot.objectKey(),
            snapshot.contentHash(),
            snapshot.sourceUpdatedAt(),
            snapshot.syncedAt(),
            snapshot.traceId());
    }
}
