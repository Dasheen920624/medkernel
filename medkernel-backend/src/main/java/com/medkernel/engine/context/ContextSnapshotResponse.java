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
    QualityStatus qualityStatus,
    List<MissingFieldEntry> missingFields,
    Map<String, String> mappingStatus,
    Instant createdAt,
    String traceId
) {}
