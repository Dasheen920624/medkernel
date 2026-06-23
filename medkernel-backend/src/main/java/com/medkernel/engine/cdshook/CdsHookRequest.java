package com.medkernel.engine.cdshook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * OPT-02 CDS Hooks 风格触发请求契约。
 */
public record CdsHookRequest(
    @NotNull ClinicalEventTriggerPoint hook,
    @NotBlank @Size(max = 128) String hookInstance,
    @NotBlank String patientId,
    String encounterId,
    String sourceSystem,
    @NotNull JsonNode context,
    JsonNode prefetch,
    String cdsHookVersion
) {
    public CdsHookRequest {
        if (hook != null) {
            CdsHookContract.requireSupportedHook(hook.wireValue());
        }
        hookInstance = trimToNull(hookInstance);
        patientId = trimToNull(patientId);
        encounterId = trimToNull(encounterId);
        sourceSystem = trimToNull(sourceSystem);
        context = normalizedContext(context, patientId, encounterId, sourceSystem);
        prefetch = prefetch == null ? NullNode.getInstance() : prefetch.deepCopy();
        cdsHookVersion = CdsHookContract.normalizeVersion(cdsHookVersion);
    }

    private static JsonNode normalizedContext(JsonNode context,
                                              String patientId,
                                              String encounterId,
                                              String sourceSystem) {
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        if (context != null && context.isObject()) {
            normalized.setAll((ObjectNode) context.deepCopy());
        } else if (context != null && !context.isNull()) {
            normalized.set("payload", context.deepCopy());
        }
        putIfPresent(normalized, "patientId", patientId);
        putIfPresent(normalized, "encounterId", encounterId);
        putIfPresent(normalized, "sourceSystem", sourceSystem);
        return normalized;
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
