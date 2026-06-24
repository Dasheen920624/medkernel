package com.medkernel.engine.context;

import java.time.Instant;

import com.medkernel.engine.context.canonical.ClinicalSetting;

/**
 * 临床事件元数据响应，不含原始 payload。
 */
public record ClinicalEventDetailResponse(
    String eventId,
    ClinicalEventType eventType,
    ClinicalEventTriggerPoint triggerPoint,
    String patientId,
    String encounterId,
    ClinicalSetting clinicalSetting,
    String sourceSystem,
    String runtimeReleaseId,
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
