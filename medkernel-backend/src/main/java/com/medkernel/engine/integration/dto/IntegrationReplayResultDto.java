package com.medkernel.engine.integration.dto;

/**
 * 集成死信人工重放结果；原死信保留为审计证据，新建补偿消息继续异步处理。
 */
public record IntegrationReplayResultDto(
    String sourceMessageId,
    String replayMessageId,
    String traceId,
    String status,
    boolean blocksMainFlow,
    String message
) {
}
