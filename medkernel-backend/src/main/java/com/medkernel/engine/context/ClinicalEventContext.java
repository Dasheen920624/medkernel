package com.medkernel.engine.context;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
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
    @NotBlank String patientId,
    String encounterId,
    String contextSnapshotId,
    String sourceSystem,
    @NotBlank String packageVersion,
    @NotBlank String payloadDigest,
    @NotNull Instant occurredAt,
    @NotBlank String triggerSource,
    @NotBlank String traceId,
    @NotNull JsonNode payload,
    @NotNull List<ClinicalCodeMappingAnchor> codeMappingAnchors
) {
    public ClinicalEventContext {
        if (orgScope == null) {
            orgScope = OrgScope.tenant(tenantId);
        }
        if (triggerSource == null || triggerSource.isBlank()) {
            String source = sourceSystem == null || sourceSystem.isBlank() ? "UNKNOWN" : sourceSystem;
            triggerSource = source + ":" + (eventType == null ? "UNKNOWN" : eventType.name());
        }
        payload = payload == null ? NullNode.getInstance() : payload.deepCopy();
        codeMappingAnchors = codeMappingAnchors == null ? List.of() : List.copyOf(codeMappingAnchors);
    }

    public String triggerPoint() {
        return eventType == null ? "UNKNOWN" : eventType.name();
    }
}
