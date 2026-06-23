package com.medkernel.engine.security.bootstrap;

import jakarta.validation.constraints.NotBlank;

/**
 * 初始内置超级管理员密码设置入参。
 */
public record BootstrapPasswordRequest(
    @NotBlank String token,
    @NotBlank String username,
    @NotBlank String password
) {
    public String usernameNormalized() {
        return username.trim();
    }
}
