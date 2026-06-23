package com.medkernel.engine.datasvc.export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 引擎数据异步导出提交请求体（DATASVC-01）。
 *
 * <p>{@code confirmationId} 为当前操作者已确认的导出范围 ID；{@code idempotencyKey} 为幂等键；
 * {@code windowDays} 时间窗（≤0 取默认 90 天，须与确认范围一致）。
 */
public record EngineDataExportSubmitRequest(
    @NotNull EngineDataExportType exportType,
    int windowDays,
    @NotBlank String confirmationId,
    @NotBlank String idempotencyKey
) {
}
