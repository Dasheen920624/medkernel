package com.medkernel.engine.integration.inbound;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.context.ClinicalEventType;
import com.medkernel.engine.context.canonical.ClinicalSetting;

/**
 * 集成接入域提交给临床事件域的稳定命令。
 */
public record InboundClinicalEventCommand(
    String eventId,
    ClinicalEventType eventType,
    String patientId,
    String encounterId,
    ClinicalSetting clinicalSetting,
    String sourceSystem,
    ClinicalEventTriggerPoint triggerPoint,
    String idempotencyKey,
    String runtimeReleaseId,
    JsonNode payload,
    Instant occurredAt
) {
}
