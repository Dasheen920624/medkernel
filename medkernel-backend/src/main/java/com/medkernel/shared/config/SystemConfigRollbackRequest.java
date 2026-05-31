package com.medkernel.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 配置中心回滚请求。
 *
 * @param reason 回滚原因，写入配置历史与审计快照
 * @param confirmedHighRisk 高危配置回滚影响二次确认
 */
public record SystemConfigRollbackRequest(
    @NotBlank @Size(max = 500) String reason,
    Boolean confirmedHighRisk
) {
}
