package com.medkernel.engine.security.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 启用/停用成员账号入参：状态须为启用、停用或锁定之一。
 */
public record SetStatusRequest(
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ACTIVE|DISABLED|LOCKED", message = "状态只能是启用、停用或锁定")
    String status
) {}
