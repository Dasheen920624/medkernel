package com.medkernel.engine.security.bootstrap;

import jakarta.validation.constraints.NotBlank;

/**
 * MFA 验证请求：使用已绑定的 TOTP secret 校验当前 6 位验证码。
 */
public record BootstrapMfaVerifyRequest(
    @NotBlank(message = "MFA 验证码不能为空")
    String code
) {}
