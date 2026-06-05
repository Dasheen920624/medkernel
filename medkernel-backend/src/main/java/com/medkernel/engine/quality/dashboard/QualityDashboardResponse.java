package com.medkernel.engine.quality.dashboard;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.quality.value.ValueMetricSummaryResponse;

/**
 * 质控驾驶舱聚合响应。
 */
public record QualityDashboardResponse(
    QualityDashboardSummary summary,
    List<QualityDashboardHeatmapCell> heatmap,
    ValueMetricSummaryResponse valueMetrics,
    List<QualityDashboardAlertResponse> activeAlerts,
    Instant generatedAt
) {}
