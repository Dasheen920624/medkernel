package com.medkernel.engine.security.bootstrap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 首发平台管理员密码设置入参。
 */
public record BootstrapPasswordRequest(
    @NotBlank String token,
    String tenantId,
    @NotBlank String username,
    @NotBlank @Size(min = 8, message = "首发账号密码至少 8 位") String password
) {
    public String tenantOrDefault() {
        return tenantId == null || tenantId.isBlank() ? "t-1" : tenantId.trim();
    }

    public String usernameNormalized() {
        return username.trim();
    }
}
