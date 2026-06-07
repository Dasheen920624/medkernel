package com.medkernel.compliance.user;

import jakarta.validation.constraints.NotNull;

/**
 * 创建租户用户。平台凭证与外部身份共用同一个用户主体。
 */
public record ComplianceUserCreateRequest(
    @NotNull Boolean credentialManaged,
    String userId,
    String displayName,
    String username,
    String roleCode,
    String initialPassword
) {
}
