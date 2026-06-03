package com.medkernel.engine.context;

import java.time.Instant;

/**
 * 临床事件死信证据响应，来源于 outbox DEAD 记录。
 */
public record ClinicalEventDeadLetterResponse(
    String deadLetterId,
    String eventId,
    String traceId,
    Integer retryCount,
    String errorCode,
    Instant createdAt,
    Instant nextAttemptAt
) {}
