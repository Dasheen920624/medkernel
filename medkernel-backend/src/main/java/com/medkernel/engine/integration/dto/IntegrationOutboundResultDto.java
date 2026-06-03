package com.medkernel.engine.integration.dto;

/**
 * 第三方对接总线出站异步投递登记结果。
 */
public record IntegrationOutboundResultDto(
    String messageId,
    String traceId,
    String adapterId,
    String status,
    boolean blocksMainFlow,
    boolean compensationRequired,
    String message
) {
}
