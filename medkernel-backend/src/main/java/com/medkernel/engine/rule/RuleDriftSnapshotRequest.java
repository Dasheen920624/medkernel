package com.medkernel.engine.rule;

import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 规则漂移监测请求，按生产执行日志窗口与回测基线对比。
 */
public record RuleDriftSnapshotRequest(
    @NotNull Instant windowStart,
    @NotNull Instant windowEnd,
    @Size(max = 64) String baselineBacktestId,
    @DecimalMin("0.0") @DecimalMax("1.0") Double threshold
) {}
