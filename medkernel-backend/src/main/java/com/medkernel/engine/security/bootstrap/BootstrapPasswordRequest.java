package com.medkernel.engine.security.bootstrap;

import jakarta.validation.constraints.NotBlank;

import com.medkernel.shared.context.PlatformTenant;

/**
 * 首发内置超级管理员密码设置入参。
 */
public record BootstrapPasswordRequest(
    @NotBlank String token,
    String tenantId,
    @NotBlank String username,
    @NotBlank String password
) {
    public String tenantOrDefault() {
        return PlatformTenant.tenantOrPlatform(tenantId);
    }

    public String usernameNormalized() {
        return username.trim();
    }
}
