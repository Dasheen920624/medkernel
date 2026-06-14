package com.medkernel.engine.datasvc;

import java.time.Instant;

/**
 * 规则使用统计聚合行（DATASVC-01 PR1，D2 去标识）。
 *
 * <p>从 {@code rule_execution_log} 真实执行事实按 {@code rule_id} 聚合：执行总数、命中数、失败数、最近执行时间。
 * 仅聚合计量，不含患者标识（D2）；禁伪造命中/采纳率（铁律 #1）。
 */
public record RuleUsageStat(
    String ruleId,
    long totalExecutions,
    long hitCount,
    long failedCount,
    Instant lastExecutedAt
) {}
