package com.medkernel.compliance.datapermission;

/**
 * SYS-06 数据权限策略适用动作。
 */
public enum DataPermissionAction {
    /** 读取原始或脱敏数据。 */
    READ,
    /** 导出敏感数据；审批流在 SYS-06 后续 PR 接入。 */
    EXPORT
}
