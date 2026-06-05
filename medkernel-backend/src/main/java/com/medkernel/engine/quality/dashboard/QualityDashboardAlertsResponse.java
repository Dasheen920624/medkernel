package com.medkernel.engine.quality.dashboard;

import java.util.List;

/**
 * 质控驾驶舱预警分页响应。
 */
public record QualityDashboardAlertsResponse(
    List<QualityDashboardAlertResponse> items,
    int offset,
    int limit,
    long total,
    boolean hasNext
) {}
