package com.medkernel.engine.mpi;

/**
 * 患者主索引拆分结果 DTO。
 */
public record MpiSplitResult(
    String status,
    String sourceMpiId,
    String targetMpiId,
    String message
) {}
