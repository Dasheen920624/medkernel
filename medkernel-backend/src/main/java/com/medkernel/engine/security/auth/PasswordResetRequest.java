package com.medkernel.engine.security.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 受控密码重置请求：凭一次性 token 设置临时口令，随后仍需强制改密。
 */
public record PasswordResetRequest(
    @NotBlank(message = "租户不能为空")
    String tenantId,
    @NotBlank(message = "用户名不能为空")
    String username,
    @NotBlank(message = "重置 token 不能为空")
    String token,
    @NotBlank(message = "新密码不能为空")
    String newPassword
) {}
