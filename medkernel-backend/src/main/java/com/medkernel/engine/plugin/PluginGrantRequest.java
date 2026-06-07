package com.medkernel.engine.plugin;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 插件授权请求。
 *
 * @param capabilityKeys 待授权能力键
 * @param approvalReason 审批理由
 * @param clinicalSafetyConfirmed 是否已完成临床安全确认
 */
public record PluginGrantRequest(
    @NotEmpty List<@Size(max = 128) String> capabilityKeys,
    @Size(max = 500) String approvalReason,
    boolean clinicalSafetyConfirmed
) {
}
