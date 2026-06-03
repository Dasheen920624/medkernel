package com.medkernel.engine.integration.dto;

import java.time.Instant;
import java.util.List;

/**
 * AdapterHub 接入编排实时状态。
 */
public record AdapterHubStatus(
    int totalAdapters,
    int activeAdapters,
    int suspendedAdapters,
    int healthyAdapters,
    int notConnectedAdapters,
    int misconfiguredAdapters,
    int mappedAdapters,
    Instant generatedAt,
    List<AdapterHubSourceStatus> sources
) {}
