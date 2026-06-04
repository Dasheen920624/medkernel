package com.medkernel.engine.recommendation;

/**
 * 推荐提醒闭环统计：全部数值均来自当前租户持久化推荐卡状态。
 */
public record RecommendationStatsResponse(
    long totalCount,
    long pendingCount,
    long acceptedCount,
    long rejectedCount,
    long dismissedCount,
    long deferredCount,
    long suppressedCount,
    long expiredCount,
    double acceptanceRatePercent,
    String traceId
) {}
