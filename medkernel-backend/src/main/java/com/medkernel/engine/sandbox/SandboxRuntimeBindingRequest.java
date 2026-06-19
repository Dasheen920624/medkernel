package com.medkernel.engine.sandbox;

import jakarta.validation.constraints.NotBlank;

/** 激活沙盘运行绑定请求；版本由配置包权威记录解析，调用方不得重复填写。 */
public record SandboxRuntimeBindingRequest(
    @NotBlank String packageOwnerTenantId,
    @NotBlank String packageId
) {
}
