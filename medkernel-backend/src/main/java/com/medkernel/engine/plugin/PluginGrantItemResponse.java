package com.medkernel.engine.plugin;

import java.time.Instant;

/**
 * 插件能力授权项响应。
 *
 * @param grantId 授权 ID
 * @param capabilityKey 能力键
 * @param capabilityType 能力类型
 * @param serviceContractId 服务契约 ID
 * @param status 授权状态
 * @param clinicalSafetyConfirmed 是否完成临床安全确认
 * @param grantedAt 授权时间
 */
public record PluginGrantItemResponse(
    String grantId,
    String capabilityKey,
    PluginCapabilityType capabilityType,
    String serviceContractId,
    PluginGrantStatus status,
    boolean clinicalSafetyConfirmed,
    Instant grantedAt
) {
}
