package com.medkernel.compliance.user;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户角色与组织范围分配请求。
 */
public record ComplianceUserRoleRequest(
    @NotBlank(message = "角色编码不能为空")
    String roleCode,
    @NotBlank(message = "作用域级别不能为空")
    String scopeLevel,
    @NotBlank(message = "作用域编码不能为空")
    String scopeCode
) {
}
