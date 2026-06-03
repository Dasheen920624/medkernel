package com.medkernel.engine.integration.dto;

import java.time.Instant;

/**
 * 区域协同来源响应。
 */
public record RegionalSourceResponse(
    String sourceId,
    String regionalNetworkName,
    String sourceOrganizationId,
    String sourceOrganizationName,
    String trustLevel,
    String evidenceText,
    String adapterId,
    String onboardingId,
    String orgPath,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
