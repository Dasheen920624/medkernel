package com.medkernel.compliance.datapermission;

/**
 * SYS-06 数据权限策略状态。
 */
public enum DataPermissionStatus {
    /** 启用，参与行列门禁判定。 */
    ACTIVE,
    /** 停用，服务层按未配置策略拒绝访问。 */
    DISABLED
}
