package com.medkernel.engine.context;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.integration.domain.IntegrationWebhookConfig;
import com.medkernel.engine.integration.dto.IntegrationOutboundRequestDto;
import com.medkernel.engine.integration.repository.IntegrationWebhookConfigRepository;
import com.medkernel.engine.integration.service.IntegrationService;

/**
 * 临床事件处理完成后登记客户回调出站消息。
 *
 * <p>只登记到统一集成总线；启用中的 Webhook 由集成层在事务提交后签名并真实投递，
 * 不可达时诚实降级且不伪造回调成功，也不把密钥或原始 payload 放进回调摘要。
 */
@Service
public class ClinicalEventCallbackNotifier {

    private static final String CALLBACK_PROTOCOL = "Webhook";

    private final ClinicalEventRepository events;
    private final IntegrationWebhookConfigRepository webhooks;
    private final IntegrationService integration;
    private final ObjectMapper json;

    public ClinicalEventCallbackNotifier(ClinicalEventRepository events,
                                         IntegrationWebhookConfigRepository webhooks,
                                         IntegrationService integration,
                                         ObjectMapper json) {
        this.events = events;
        this.webhooks = webhooks;
        this.integration = integration;
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyProcessed(ClinicalEventProcessedEvent processed) {
        events.findByEventIdAndTenantId(processed.eventId(), processed.tenantId())
            .filter(event -> event.callbackWebhookId() != null && !event.callbackWebhookId().isBlank())
            .ifPresent(event -> registerCallback(processed, event));
    }

    private void registerCallback(ClinicalEventProcessedEvent processed, ClinicalEvent event) {
        webhooks.findByWebhookIdAndTenantId(event.callbackWebhookId(), event.tenantId())
            .filter(webhook -> "ACTIVE".equalsIgnoreCase(webhook.status()))
            .ifPresent(webhook -> integration.enqueueOutboundMessage(event.tenantId(),
                new IntegrationOutboundRequestDto(
                    "clinical-callback-" + event.eventId(),
                    firstNonBlank(processed.traceId(), event.traceId()),
                    webhook.webhookId(),
                    targetName(webhook),
                    CALLBACK_PROTOCOL,
                    "临床事件处理完成回调 eventId=" + event.eventId(),
                    callbackPayload(processed, event, webhook),
                    3)));
    }

    private ObjectNode callbackPayload(ClinicalEventProcessedEvent processed,
                                       ClinicalEvent event,
                                       IntegrationWebhookConfig webhook) {
        ObjectNode payload = json.createObjectNode();
        payload.put("messageType", "CLINICAL_EVENT_PROCESSED");
        payload.put("eventId", event.eventId());
        payload.put("tenantId", event.tenantId());
        payload.put("traceId", firstNonBlank(processed.traceId(), event.traceId()));
        payload.put("status", ClinicalEventStatus.PROCESSED.name());
        payload.put("eventType", event.eventType().name());
        payload.put("triggerPoint", event.triggerPoint().wireValue());
        payload.put("patientId", event.patientId());
        putNullable(payload, "encounterId", event.encounterId());
        payload.put("sourceSystem", event.sourceSystem());
        payload.put("runtimeReleaseId", event.runtimeReleaseId());
        payload.put("payloadDigest", event.payloadDigest());
        putInstant(payload, "occurredAt", event.occurredAt());
        putNullable(payload, "callbackWebhookId", webhook.webhookId());
        return payload;
    }

    private String targetName(IntegrationWebhookConfig webhook) {
        return firstNonBlank(webhook.name(), webhook.webhookId());
    }

    private void putNullable(ObjectNode payload, String field, String value) {
        if (value == null || value.isBlank()) {
            payload.putNull(field);
            return;
        }
        payload.put(field, value);
    }

    private void putInstant(ObjectNode payload, String field, Instant value) {
        if (value == null) {
            payload.putNull(field);
            return;
        }
        payload.put(field, value.toString());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
