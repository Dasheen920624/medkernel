package com.medkernel.engine.context;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

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
    String sourceSystem,
    @NotBlank String packageVersion,
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
        if (packageVersion != null) {
            packageVersion = packageVersion.trim();
        }
        if (idempotencyKey != null) {
            idempotencyKey = idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        }
        if (callbackWebhookId != null) {
            callbackWebhookId = callbackWebhookId.isBlank() ? null : callbackWebhookId.trim();
        }
    }
}
