package com.medkernel.engine.datasvc.export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 引擎数据异步导出提交请求体（DATASVC-01）。
 *
 * <p>{@code approvalId} 为已通过的 SYS-06 导出审批 ID（不绕审批）；{@code idempotencyKey} 幂等键（规范要求）；
 * {@code windowDays} 时间窗（≤0 取默认 90 天，须与审批范围一致）。
 */
public record EngineDataExportSubmitRequest(
    @NotNull EngineDataExportType exportType,
    int windowDays,
    @NotBlank String approvalId,
    @NotBlank String idempotencyKey
) {
}
