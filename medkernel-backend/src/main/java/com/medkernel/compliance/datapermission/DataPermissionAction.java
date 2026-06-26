package com.medkernel.compliance.datapermission;

/**
 * SYS-06 数据权限策略适用动作。
 */
public enum DataPermissionAction {
    /** 读取原始或脱敏数据。 */
    READ,
    /** 导出敏感数据；必须绑定现行审批与审计契约。 */
    EXPORT
}
