package com.medkernel.engine.context;

/**
 * 临床事件已处理事件，供后续 snapshot、规则、路径或推荐链路监听。
 */
public record ClinicalEventProcessedEvent(
    String eventId,
    String tenantId,
    String traceId,
    ClinicalEventContext context
) {}
