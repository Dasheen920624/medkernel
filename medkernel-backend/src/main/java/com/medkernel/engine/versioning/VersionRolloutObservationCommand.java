package com.medkernel.engine.versioning;

import java.time.Instant;

/**
 * 记录灰度观测命令。
 */
public record VersionRolloutObservationCommand(
    String tenantId,
    String planId,
    Integer stageIndex,
    Long sampleCount,
    Long hitCount,
    Long blockCount,
    Long manualRejectionCount,
    Long anomalyCount,
    Instant observedAt,
    String actor,
    String traceId
) {
}
