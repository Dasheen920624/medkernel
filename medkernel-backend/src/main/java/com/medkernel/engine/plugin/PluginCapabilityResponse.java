package com.medkernel.engine.plugin;

/**
 * 插件能力声明响应。
 *
 * @param capabilityKey 能力键
 * @param capabilityType 能力类型
 * @param serviceContractId 绑定的服务契约 ID
 * @param serviceContractTitle 服务契约中文名称
 * @param clinicalData 是否涉及临床数据
 */
public record PluginCapabilityResponse(
    String capabilityKey,
    PluginCapabilityType capabilityType,
    String serviceContractId,
    String serviceContractTitle,
    boolean clinicalData
) {
}
