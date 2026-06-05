package com.medkernel.engine.quality.dashboard;

import java.time.Instant;

/**
 * 质控驾驶舱下钻明细。
 */
public record QualityDashboardDrilldownItem(
    String sourceType,
    String sourceId,
    String departmentId,
    String severity,
    String status,
    String title,
    String evidenceSummary,
    Instant occurredAt,
    String traceId
) {}
