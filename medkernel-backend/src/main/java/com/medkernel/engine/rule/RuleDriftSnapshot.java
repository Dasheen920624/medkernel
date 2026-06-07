package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 规则上线后命中率漂移快照，基于真实执行日志窗口计算。
 */
@Table("rule_drift_snapshot")
public record RuleDriftSnapshot(
    @Id Long id,
    @Column("drift_id") String driftId,
    @Column("tenant_id") String tenantId,
    @Column("rule_id") String ruleId,
    @Column("version_id") String versionId,
    @Column("baseline_backtest_id") String baselineBacktestId,
    @Column("window_start") Instant windowStart,
    @Column("window_end") Instant windowEnd,
    @Column("sample_count") long sampleCount,
    @Column("hit_count") long hitCount,
    @Column("baseline_fire_rate") double baselineFireRate,
    @Column("current_fire_rate") double currentFireRate,
    @Column("drift_delta") double driftDelta,
    double threshold,
    RuleDriftStatus status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {}
