package com.medkernel.engine.security.auth;

import jakarta.validation.constraints.NotBlank;

import com.medkernel.shared.context.PlatformTenant;

/**
 * 平台账号登录入参：用户名与密码必填，租户可选（缺省回退唯一平台主租户）。
 */
public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password,
    String tenantId
) {
    public String tenantOrDefault() {
        return PlatformTenant.tenantOrPlatform(tenantId);
    }
}
