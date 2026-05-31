package com.medkernel.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 配置中心配置变更请求。
 *
 * @param value  配置值，按 valueType 做业务校验
 * @param reason 变更原因，写入配置历史与审计快照
 * @param expectedVersion 前端读取到的配置版本，用于避免覆盖他人变更
 * @param confirmedHighRisk 高危配置影响二次确认
 */
public record SystemConfigUpdateRequest(
    @NotBlank String value,
    @Size(max = 500) String reason,
    Long expectedVersion,
    Boolean confirmedHighRisk
) {
}
