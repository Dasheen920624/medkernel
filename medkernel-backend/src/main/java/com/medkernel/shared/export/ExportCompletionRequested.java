package com.medkernel.shared.export;

/**
 * 真实导出文件生成完成后的统一登记事件。
 *
 * @param tenantId      租户 ID
 * @param idempotencyKey 导出确认与任务共享的幂等键
 * @param jobId          后端导出任务 ID
 * @param reason         完成登记原因
 * @param actor          执行账号
 */
public record ExportCompletionRequested(
    String tenantId,
    String idempotencyKey,
    String jobId,
    String reason,
    String actor
) {
}
