package com.medkernel.engine.integration.dto;

import java.time.Instant;
import java.util.List;

/**
 * AdapterHub 必接外部系统覆盖状态。
 */
public record AdapterHubRequiredSourceStatus(
    String sourceSystem,
    String label,
    String adapterId,
    String adapterName,
    String protocolType,
    String status,
    String healthStatus,
    int mappedFieldCount,
    Instant lastHeartbeatAt,
    boolean ready,
    List<String> gaps
) {}
