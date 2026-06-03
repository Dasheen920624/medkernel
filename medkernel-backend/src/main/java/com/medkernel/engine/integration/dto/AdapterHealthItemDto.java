package com.medkernel.engine.integration.dto;

import java.time.Instant;

/**
 * 第三方适配器健康目录单项。
 */
public record AdapterHealthItemDto(
    String adapterId,
    String name,
    String protocolType,
    String status,
    String healthStatus,
    Long rttMs,
    Instant lastHeartbeatAt,
    String message
) {}
