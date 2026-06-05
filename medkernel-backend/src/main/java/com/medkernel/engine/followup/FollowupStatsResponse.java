package com.medkernel.engine.followup;

/**
 * 随访作用域统计响应。
 *
 * <p>统计值均来自当前租户及可选患者筛选下的数据库聚合，不从前端当前页派生。
 */
public record FollowupStatsResponse(
    long totalPlans,
    long activePlans,
    long totalTasks,
    long completedTasks,
    long abnormalReturnTasks,
    double taskCompletionRatePercent,
    double abnormalReturnRatePercent,
    String traceId
) {
}
