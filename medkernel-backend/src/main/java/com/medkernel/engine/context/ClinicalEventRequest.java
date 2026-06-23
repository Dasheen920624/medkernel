package com.medkernel.engine.context;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.cdshook.CdsHookRequest;
import com.medkernel.engine.context.canonical.ClinicalSetting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 临床事件接收请求。
 */
public record ClinicalEventRequest(
    @NotBlank String eventId,
    @NotNull ClinicalEventType eventType,
    @NotBlank String patientId,
    String encounterId,
    @NotNull ClinicalSetting clinicalSetting,
    String sourceSystem,
    @NotNull ClinicalEventTriggerPoint triggerPoint,
    @Size(max = 128) String idempotencyKey,
    @Size(max = 64) String callbackWebhookId,
    @NotNull JsonNode payload,
    @NotNull Instant occurredAt
) {
    public ClinicalEventRequest {
        if (eventId != null) {
            eventId = eventId.trim();
        }
        if (patientId != null) {
            patientId = patientId.trim();
        }
        if (encounterId != null) {
            encounterId = encounterId.isBlank() ? null : encounterId.trim();
        }
        if (sourceSystem != null) {
            sourceSystem = sourceSystem.isBlank() ? null : sourceSystem.trim();
        }
        if (idempotencyKey != null) {
            idempotencyKey = idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        }
        if (callbackWebhookId != null) {
            callbackWebhookId = callbackWebhookId.isBlank() ? null : callbackWebhookId.trim();
        }
    }

    public CdsHookRequest toCdsHookRequest() {
        String hookInstance = idempotencyKey == null || idempotencyKey.isBlank() ? eventId : idempotencyKey;
        ObjectNode context = JsonNodeFactory.instance.objectNode()
            .put("eventId", eventId)
            .put("eventType", eventType == null ? null : eventType.name())
            .put("clinicalSetting", clinicalSetting == null ? null : clinicalSetting.name())
            .put("triggerPoint", triggerPoint == null ? null : triggerPoint.wireValue());
        if (payload != null && payload.isObject()) {
            context.setAll((ObjectNode) payload.deepCopy());
        } else if (payload != null && !payload.isNull()) {
            context.set("payload", payload.deepCopy());
        }
        return new CdsHookRequest(
            triggerPoint,
            hookInstance,
            patientId,
            encounterId,
            sourceSystem,
            context,
            null,
            null);
    }
}
