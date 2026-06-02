package com.medkernel.engine.context;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * snapshot 详情响应体（创建与按 ID 查询共用）。
 */
public record ContextSnapshotResponse(
    String snapshotId,
    ContextSnapshotStatus status,
    ContextSnapshotResources resources,
    String packageVersion,
    String knowledgePackageVersion,
    String rulePackageVersion,
    String pathwayPackageVersion,
    QualityStatus qualityStatus,
    List<MissingFieldEntry> missingFields,
    Map<String, String> mappingStatus,
    Instant createdAt,
    String traceId
) {

    public ContextSnapshotResponse(
            String snapshotId,
            ContextSnapshotStatus status,
            QualityStatus qualityStatus,
            List<MissingFieldEntry> missingFields,
            Map<String, String> mappingStatus,
            Instant createdAt,
            String traceId) {
        this(
            snapshotId, status, null, null, null, null, null,
            qualityStatus, missingFields, mappingStatus, createdAt, traceId
        );
    }
}
