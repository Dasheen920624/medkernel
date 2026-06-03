package com.medkernel.engine.mpi;

/**
 * 患者主索引合并结果。
 */
public record MpiMergeResult(
    String status,
    String sourceMpiId,
    String targetMpiId,
    String reviewId,
    String riskLevel,
    String message
) {}
