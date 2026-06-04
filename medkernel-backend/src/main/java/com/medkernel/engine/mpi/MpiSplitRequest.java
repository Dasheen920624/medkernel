package com.medkernel.engine.mpi;

import jakarta.validation.constraints.NotBlank;

/**
 * 患者主索引拆分请求 DTO。
 */
public record MpiSplitRequest(
    @NotBlank(message = "人工核查理由不能为空")
    String reviewReason
) {}
