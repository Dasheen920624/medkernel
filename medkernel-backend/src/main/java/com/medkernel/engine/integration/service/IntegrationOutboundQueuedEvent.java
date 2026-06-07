package com.medkernel.engine.integration.service;

/**
 * 出站消息事务提交后的异步投递事件。
 */
public record IntegrationOutboundQueuedEvent(String tenantId, String messageId) {
}
