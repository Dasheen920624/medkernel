package com.medkernel.engine.integration.dto;

import java.time.Instant;
import java.util.List;

/**
 * 第三方适配器周期健康探测批次结果，用于运维与可观测汇总。
 */
public record IntegrationHealthProbeResultDto(
    int total,
    int healthy,
    int notConnected,
    int misconfigured,
    Instant checkedAt,
    List<IntegrationHealthProbeItemDto> adapters
) {
}
