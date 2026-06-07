package com.medkernel.engine.rule;

import java.time.Instant;

/**
 * 规则漂移监测响应。
 */
public record RuleDriftSnapshotResponse(
    String driftId,
    String ruleId,
    String versionId,
    String baselineBacktestId,
    Instant windowStart,
    Instant windowEnd,
    long sampleCount,
    long hitCount,
    double baselineFireRate,
    double currentFireRate,
    double driftDelta,
    double threshold,
    RuleDriftStatus status,
    Instant createdAt,
    String traceId
) {}
