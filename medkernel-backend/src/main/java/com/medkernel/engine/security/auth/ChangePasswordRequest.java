package com.medkernel.engine.security.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 自助改密入参：校验原密码和运行时强密码策略后设置新密码，并清除"首登须改密"标志。
 */
public record ChangePasswordRequest(
    @NotBlank String oldPassword,
    @NotBlank String newPassword
) {}
