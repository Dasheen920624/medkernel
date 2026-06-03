package com.medkernel.engine.mpi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 高危患者主索引合并人工确认请求。
 */
public record MpiMergeReviewConfirmRequest(
    @NotBlank(message = "人工确认理由不能为空")
    @Size(max = 500, message = "人工确认理由不能超过 500 字")
    String reviewReason
) {}
