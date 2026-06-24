package com.medkernel.engine.quality.dashboard;

import java.util.List;

/**
 * 质控驾驶舱下钻响应。
 */
public record QualityDashboardDrilldownResponse(
    QualityDashboardDrilldownType type,
    List<QualityDashboardDrilldownItem> items,
    QualityEvidenceExport evidenceExport,
    int offset,
    int limit,
    long total,
    boolean hasNext
) {}
