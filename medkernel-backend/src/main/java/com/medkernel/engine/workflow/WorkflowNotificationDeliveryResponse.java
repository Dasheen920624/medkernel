package com.medkernel.engine.workflow;

import java.time.Instant;

import com.medkernel.engine.integration.domain.IntegrationMessageLog;

/**
 * 通知外发补偿状态响应 DTO；只反映集成日志事实，不代表真实外部已送达。
 */
public record WorkflowNotificationDeliveryResponse(
    String channelCode,
    String channelName,
    String status,
    boolean compensationRequired,
    Integer retryCount,
    Integer maxRetries,
    Instant updatedAt,
    String errorMessage
) {
    private static final String STATUS_SUCCESS = "SUCCESS";

    static WorkflowNotificationDeliveryResponse from(
            String channelCode,
            String channelName,
            IntegrationMessageLog log) {
        return new WorkflowNotificationDeliveryResponse(
            channelCode,
            channelName,
            log.status(),
            !STATUS_SUCCESS.equals(log.status()),
            log.retryCount(),
            log.maxRetries(),
            log.updatedAt(),
            log.errorMessage());
    }
}
