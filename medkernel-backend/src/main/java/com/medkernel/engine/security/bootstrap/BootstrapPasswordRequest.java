package com.medkernel.engine.security.bootstrap;

import jakarta.validation.constraints.NotBlank;

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
        return tenantId == null || tenantId.isBlank() ? "t-1" : tenantId.trim();
    }

    public String usernameNormalized() {
        return username.trim();
    }
}
