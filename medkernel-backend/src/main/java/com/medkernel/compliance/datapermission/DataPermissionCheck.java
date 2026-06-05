package com.medkernel.compliance.datapermission;

import java.util.List;

import com.medkernel.shared.context.OrgScope;

/**
 * SYS-06 行列数据权限门禁请求。
 *
 * @param tenantId         租户 ID
 * @param resourceType     业务资源类型
 * @param action           访问动作
 * @param targetScope      目标数据所属组织范围
 * @param requestedColumns 请求读取或导出的字段名
 */
public record DataPermissionCheck(
    String tenantId,
    String resourceType,
    DataPermissionAction action,
    OrgScope targetScope,
    List<String> requestedColumns
) {
}
