package com.medkernel.engine.context;

import java.time.Instant;

/**
 * 临床事件元数据响应，不含原始 payload。
 */
public record ClinicalEventDetailResponse(
    String eventId,
    ClinicalEventType eventType,
    ClinicalEventTriggerPoint triggerPoint,
    String patientId,
    String encounterId,
    String sourceSystem,
    String packageVersion,
    String callbackWebhookId,
    ClinicalEventStatus status,
    String payloadDigest,
    String errorCode,
    String errorClass,
    Integer retryCount,
    String rootEventId,
    Instant occurredAt,
    Instant receivedAt,
    String traceId
) {}
