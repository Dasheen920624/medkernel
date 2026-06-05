package com.medkernel.engine.quality.value;

import java.time.Instant;

/**
 * 价值指标下钻事实行。
 */
public record ValueMetricDrilldownItem(
    String sourceType,
    String sourceId,
    String patientId,
    String encounterId,
    String departmentId,
    String status,
    String reason,
    Instant occurredAt,
    String traceId
) {}
