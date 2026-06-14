package com.medkernel.shared.export;

/**
 * 导出审批闸（异步导出提交前的「不绕审批」校验入口）。
 *
 * <p>引擎侧导出来源在提交导出作业前调用本闸，校验已有通过的导出审批且资源类型 + 范围一致；
 * 实现位于合规导出审批层（依赖审批仓储），引擎侧只依赖本 shared 接口（SYS-02 依赖方向：引擎 → shared）。
 * 校验失败抛结构化异常（无审批/未通过 → 403，资源类型或范围不一致 → 409），不绕审批、不泄漏内部。
 */
public interface ExportApprovalGate {

    /**
     * 校验给定导出审批已通过且与本次导出的资源类型、范围一致；不满足即抛结构化异常。
     *
     * @param tenantId 租户
     * @param approvalId 导出审批 ID
     * @param resourceType 本次导出资源类型（须与审批一致）
     * @param requestSnapshot 本次导出范围快照 JSON（须与审批范围 JSON 等价）
     */
    void requireApprovedForExport(String tenantId, String approvalId, String resourceType, String requestSnapshot);
}
