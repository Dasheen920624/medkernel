package com.medkernel.engine.quality.dashboard;

import java.util.List;

/**
 * 质量风险概览预警分页响应。
 */
public record QualityDashboardAlertsResponse(
    List<QualityDashboardAlertResponse> items,
    int offset,
    int limit,
    long total,
    boolean hasNext
) {}
