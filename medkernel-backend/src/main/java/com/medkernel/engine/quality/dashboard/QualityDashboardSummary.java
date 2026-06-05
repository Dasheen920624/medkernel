package com.medkernel.engine.quality.dashboard;

/**
 * 质控驾驶舱院级汇总。
 *
 * @param totalFindings 质控问题总数
 * @param openFindings 待闭环问题数
 * @param closedFindings 已关闭问题数
 * @param waivedFindings 已豁免问题数
 * @param overdueRectificationTasks 逾期整改任务数
 * @param activeAlerts 当前打开预警数
 */
public record QualityDashboardSummary(
    long totalFindings,
    long openFindings,
    long closedFindings,
    long waivedFindings,
    long overdueRectificationTasks,
    long activeAlerts
) {}
