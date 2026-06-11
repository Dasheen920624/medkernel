package com.medkernel.compliance.user;

/**
 * 用户当前有效的角色与组织范围。
 */
public record ComplianceUserRole(
    String code,
    String displayName,
    String scopeLevel,
    String scopeCode,
    String scopeName
) {
}
