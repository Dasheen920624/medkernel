package com.medkernel.compliance.identitybinding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 解除外部身份绑定请求。
 */
public record IdentityBindingUnbindRequest(
    @NotBlank(message = "解绑原因不能为空")
    @Size(max = 512, message = "解绑原因不能超过 512 个字符")
    String reason,

    @NotNull(message = "期望版本不能为空")
    @Positive(message = "期望版本必须大于 0")
    Long expectedVersion
) {
}
