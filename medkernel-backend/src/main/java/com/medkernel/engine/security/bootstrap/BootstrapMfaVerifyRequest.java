package com.medkernel.engine.security.bootstrap;

import jakarta.validation.constraints.NotBlank;

/**
 * 多因素认证请求：使用已绑定的认证器密钥校验当前 6 位验证码。
 */
public record BootstrapMfaVerifyRequest(
    @NotBlank(message = "验证码不能为空")
    String code
) {}
