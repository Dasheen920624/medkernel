package com.medkernel.engine.integration.dto;

import java.time.Instant;

/**
 * 第三方适配器周期健康探测单项结果，记录租户、适配器与诚实降级状态。
 */
public record IntegrationHealthProbeItemDto(
    String tenantId,
    String adapterId,
    String name,
    String protocolType,
    String healthStatus,
    long rttMs,
    Instant lastHeartbeatAt,
    String message
) {
}
