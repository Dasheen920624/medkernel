package com.medkernel.engine.integration.dto;

import java.time.Instant;
import java.util.List;

/**
 * AdapterHub 单个数据源接入状态。
 */
public record AdapterHubSourceStatus(
    String adapterId,
    String name,
    String protocolType,
    String status,
    String healthStatus,
    int mappedFieldCount,
    Instant lastHeartbeatAt,
    List<String> gaps
) {}
