package com.medkernel.engine.context;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.medkernel.engine.context.canonical.ClinicalSetting;
import com.medkernel.shared.context.OrgScope;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 临床事件驱动规则、路径与 CDSS 的统一上下文。
 */
public record ClinicalEventContext(
    @NotBlank String eventId,
    @NotBlank String tenantId,
    @NotNull OrgScope orgScope,
    @NotNull ClinicalEventType eventType,
    @NotNull ClinicalEventTriggerPoint clinicalTriggerPoint,
    @NotBlank String patientId,
    String encounterId,
    @NotNull ClinicalSetting clinicalSetting,
    String contextSnapshotId,
    String sourceSystem,
    @NotBlank String runtimeReleaseId,
    @NotBlank String payloadDigest,
    @NotNull Instant occurredAt,
    @NotBlank String triggerSource,
    @NotBlank String traceId,
    @NotNull ContextSnapshotResources resources,
    @NotNull JsonNode payload,
    @NotNull List<ClinicalCodeMappingAnchor> codeMappingAnchors
) {
    public ClinicalEventContext {
        if (orgScope == null) {
            orgScope = OrgScope.tenant(tenantId);
        }
        if (clinicalTriggerPoint == null) {
            clinicalTriggerPoint = fallbackTriggerPoint(eventType);
        }
        if (triggerSource == null || triggerSource.isBlank()) {
            String source = sourceSystem == null || sourceSystem.isBlank() ? "UNKNOWN" : sourceSystem;
            triggerSource = source + ":" + clinicalTriggerPoint.wireValue();
        }
        resources = Objects.requireNonNull(resources, "resources");
        payload = payload == null ? NullNode.getInstance() : payload.deepCopy();
        codeMappingAnchors = codeMappingAnchors == null ? List.of() : List.copyOf(codeMappingAnchors);
    }

    public String triggerPoint() {
        return clinicalTriggerPoint.wireValue();
    }

    public ClinicalEventContext withContextSnapshotId(String snapshotId) {
        return new ClinicalEventContext(
            eventId, tenantId, orgScope, eventType, clinicalTriggerPoint, patientId,
            encounterId, clinicalSetting, snapshotId, sourceSystem,
            runtimeReleaseId, payloadDigest,
            occurredAt, triggerSource, traceId, resources, payload, codeMappingAnchors);
    }

    private static ClinicalEventTriggerPoint fallbackTriggerPoint(ClinicalEventType eventType) {
        if (eventType == null) {
            return ClinicalEventTriggerPoint.PATIENT_VIEW;
        }
        return switch (eventType) {
            case ORDER -> ClinicalEventTriggerPoint.ORDER_SIGN;
            case REPORT -> ClinicalEventTriggerPoint.RESULT_REVIEW;
            case DISCHARGE -> ClinicalEventTriggerPoint.DISCHARGE_SIGN;
            case FOLLOWUP -> ClinicalEventTriggerPoint.FOLLOWUP_ALERT;
            case DIAGNOSIS, ADMISSION -> ClinicalEventTriggerPoint.PATIENT_VIEW;
        };
    }
}
