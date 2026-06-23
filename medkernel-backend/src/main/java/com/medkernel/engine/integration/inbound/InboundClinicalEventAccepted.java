package com.medkernel.engine.integration.inbound;

/**
 * 临床事件域接受入站数据后的最小回执。
 */
public record InboundClinicalEventAccepted(
    String eventId,
    String status
) {
}
