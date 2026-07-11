package com.medkernel.engine.quality.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 质量风险概览预警响应。
 */
public record QualityDashboardAlertResponse(
    String alertId,
    QualityDashboardAlertType alertType,
    QualityDashboardAlertStatus status,
    String departmentId,
    String sourceType,
    String sourceId,
    String severity,
    String thresholdCode,
    BigDecimal thresholdValue,
    BigDecimal actualValue,
    String title,
    String evidenceSummary,
    Instant createdAt,
    Instant updatedAt,
    String traceId
) {}
