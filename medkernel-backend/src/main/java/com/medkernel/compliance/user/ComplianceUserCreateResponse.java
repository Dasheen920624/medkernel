package com.medkernel.compliance.user;

/**
 * 用户创建结果。临时密码只在平台生成时返回一次。
 */
public record ComplianceUserCreateResponse(
    ComplianceUserDetail user,
    String tempPassword
) {
}
