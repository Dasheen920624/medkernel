package com.medkernel.engine.integration.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务提交后执行真实外部投递，避免外部网络等待占用业务事务。
 */
@Component
public class IntegrationOutboundQueuedListener {

    private final IntegrationService integrationService;

    public IntegrationOutboundQueuedListener(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(IntegrationOutboundQueuedEvent event) {
        integrationService.dispatchQueuedMessage(event.tenantId(), event.messageId());
    }
}
