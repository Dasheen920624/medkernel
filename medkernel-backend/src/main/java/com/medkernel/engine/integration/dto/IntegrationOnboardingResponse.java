package com.medkernel.engine.integration.dto;

import java.time.Instant;
import java.util.List;

/**
 * 第三方业务接口接入状态响应。
 */
public record IntegrationOnboardingResponse(
    String onboardingId,
    String name,
    String status,
    String routeType,
    String routeReference,
    String healthStatus,
    int mappedFieldCount,
    List<String> blockers,
    String sourceSystem,
    String businessScenario,
    String orgPath,
    String callbackWebhookId,
    Instant createdAt,
    Instant updatedAt
) {
}
