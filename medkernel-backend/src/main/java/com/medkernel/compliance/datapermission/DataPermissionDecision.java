package com.medkernel.compliance.datapermission;

import java.util.List;

import com.medkernel.engine.security.DataAccessLevel;

/**
 * SYS-06 数据权限门禁判定结果。
 */
public record DataPermissionDecision(
    String policyId,
    String resourceType,
    DataPermissionAction action,
    DataAccessLevel requiredLevel,
    boolean rowAllowed,
    List<String> allowedColumns,
    List<String> deniedColumns
) {
}
