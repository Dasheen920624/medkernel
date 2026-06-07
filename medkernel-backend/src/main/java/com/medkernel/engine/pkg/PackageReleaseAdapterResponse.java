package com.medkernel.engine.pkg;

import java.time.Instant;

import com.medkernel.engine.integration.domain.IntegrationAdapter;

/**
 * 配置包发布可选的统一集成适配器摘要。
 */
public record PackageReleaseAdapterResponse(
    String adapterId,
    String adapterName,
    String protocolType,
    String status,
    String healthStatus,
    Long rttMs,
    Instant lastHeartbeatAt,
    boolean connectorAvailable
) {
    public static PackageReleaseAdapterResponse from(
            IntegrationAdapter adapter,
            boolean connectorAvailable) {
        return new PackageReleaseAdapterResponse(
            adapter.adapterId(),
            adapter.name(),
            adapter.protocolType(),
            adapter.status(),
            adapter.healthStatus(),
            adapter.rttMs(),
            adapter.lastHeartbeatAt(),
            connectorAvailable);
    }
}
