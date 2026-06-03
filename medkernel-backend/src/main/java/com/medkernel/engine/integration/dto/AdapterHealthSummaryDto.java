package com.medkernel.engine.integration.dto;

import java.time.Instant;
import java.util.List;

/**
 * 第三方适配器健康目录汇总，供工作台与可观测模块读取。
 */
public record AdapterHealthSummaryDto(
    int total,
    int active,
    int suspended,
    int healthy,
    int notConnected,
    int misconfigured,
    Instant checkedAt,
    List<AdapterHealthItemDto> adapters
) {}
