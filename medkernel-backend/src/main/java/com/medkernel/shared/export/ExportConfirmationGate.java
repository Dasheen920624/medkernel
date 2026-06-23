package com.medkernel.shared.export;

/**
 * 导出确认门禁。
 *
 * <p>异步导出任务提交前必须校验确认记录属于当前租户，且资源类型和冻结范围与任务完全一致。
 */
public interface ExportConfirmationGate {

    /**
     * 校验导出确认记录；不满足时抛出结构化异常。
     *
     * @param tenantId 租户 ID
     * @param confirmationId 导出确认 ID
     * @param resourceType 导出资源类型
     * @param requestSnapshot 导出范围快照 JSON
     */
    void requireConfirmedForExport(
        String tenantId,
        String confirmationId,
        String resourceType,
        String requestSnapshot
    );
}
