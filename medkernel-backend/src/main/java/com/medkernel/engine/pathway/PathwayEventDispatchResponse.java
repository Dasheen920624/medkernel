package com.medkernel.engine.pathway;

/**
 * 临床路径接收临床事件上下文后的入口确认。
 */
public record PathwayEventDispatchResponse(
    String eventId,
    String patientId,
    String encounterId,
    String traceId
) {}
