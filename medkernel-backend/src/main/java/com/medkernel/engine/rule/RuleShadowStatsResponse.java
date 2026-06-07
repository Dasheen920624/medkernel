package com.medkernel.engine.rule;

/**
 * 规则影子运行统计，命中率按全部影子执行计算，误报率按影子命中计算。
 */
public record RuleShadowStatsResponse(
    String ruleId,
    long totalExecutions,
    long hitCount,
    long missCount,
    long falsePositiveCount,
    double hitRate,
    double falsePositiveRate,
    String traceId
) {}
