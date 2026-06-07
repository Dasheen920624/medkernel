package com.medkernel.engine.plugin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 插件能力声明请求。
 *
 * @param capabilityKey 能力键
 * @param capabilityType 能力类型
 * @param serviceContractId 绑定的服务契约 ID
 * @param clinicalData 是否涉及临床数据
 */
public record PluginCapabilityRequest(
    @NotBlank @Size(max = 128) String capabilityKey,
    @NotNull PluginCapabilityType capabilityType,
    @NotBlank @Size(max = 128) String serviceContractId,
    boolean clinicalData
) {
}
