package com.medkernel.engine.projection;

/**
 * 投影重建响应。
 */
public record ProjectionRebuildResponse(
    String syncId,
    ProjectionTargetType targetType,
    ProjectionSyncStatus status,
    int sourceCount,
    int projectionCount,
    String sourceHash,
    String projectionHash,
    String traceId,
    ProjectionSyncStatus difyExecutionStatus,
    String message
) {}
