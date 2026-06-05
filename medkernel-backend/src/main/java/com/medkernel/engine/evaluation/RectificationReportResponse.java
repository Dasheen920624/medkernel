package com.medkernel.engine.evaluation;

import java.math.BigDecimal;

/**
 * 整改闭环报告响应。
 *
 * <p>所有统计只来自 {@code rectification_task} 与其关联的 {@code quality_finding} 真实事实。
 */
public record RectificationReportResponse(
    RectificationReportStatus status,
    long totalTasks,
    long openTasks,
    long closedTasks,
    long waivedTasks,
    long overdueTasks,
    long highPriorityOpenTasks,
    BigDecimal closureRate,
    String sourceTable,
    String traceId
) {}
