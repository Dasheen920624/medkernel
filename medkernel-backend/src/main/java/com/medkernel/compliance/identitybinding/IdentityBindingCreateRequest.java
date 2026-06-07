package com.medkernel.compliance.identitybinding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建外部身份绑定请求。
 */
public record IdentityBindingCreateRequest(
    @NotBlank(message = "用户 ID 不能为空")
    @Size(max = 128, message = "用户 ID 不能超过 128 个字符")
    String userId,

    @NotNull(message = "身份来源类型不能为空")
    IdentityProviderType providerType,

    @NotBlank(message = "外部身份不能为空")
    @Size(max = 512, message = "外部身份不能超过 512 个字符")
    String externalSubject,

    @NotBlank(message = "绑定原因不能为空")
    @Size(max = 512, message = "绑定原因不能超过 512 个字符")
    String reason
) {
}
