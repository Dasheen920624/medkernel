package com.medkernel.engine.evaluation;

import jakarta.validation.constraints.NotBlank;

/**
 * 触发上下文快照自动评估计算的请求。
 */
public record EvaluationEvaluateSnapshotRequest(
    @NotBlank String contextSnapshotId,
    @NotBlank String scenarioCode
) {}
