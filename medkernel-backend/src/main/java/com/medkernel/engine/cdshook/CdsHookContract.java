package com.medkernel.engine.cdshook;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * OPT-02 CDS Hooks 风格契约版本与 6 类触发点校验。
 */
public final class CdsHookContract {
    public static final String CURRENT_VERSION = "1.0";

    private static final Set<String> COMPATIBLE_VERSIONS = Set.of("1", CURRENT_VERSION);

    private CdsHookContract() {
    }

    public static String normalizeVersion(String version) {
        if (version == null || version.isBlank()) {
            return CURRENT_VERSION;
        }
        String normalized = version.trim();
        if ("1".equals(normalized)) {
            return CURRENT_VERSION;
        }
        if (!COMPATIBLE_VERSIONS.contains(normalized)) {
            throw new ApiException(ErrorCode.ENG_EVENT_001, "CDS Hooks 契约版本不受支持: " + version);
        }
        return normalized;
    }

    public static ClinicalEventTriggerPoint requireSupportedHook(String hook) {
        try {
            ClinicalEventTriggerPoint triggerPoint = ClinicalEventTriggerPoint.fromWireValue(hook);
            if (triggerPoint == null) {
                throw new IllegalArgumentException("hook 为空");
            }
            return triggerPoint;
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.ENG_EVENT_001, "CDS Hooks 触发点不受支持: " + hook);
        }
    }

    public static List<String> supportedHooks() {
        return java.util.Arrays.stream(ClinicalEventTriggerPoint.values())
            .map(ClinicalEventTriggerPoint::wireValue)
            .toList();
    }

    public static List<String> missingRequiredContextFields(CdsHookRequest request) {
        if (request == null || request.hook() == null) {
            return List.of("hook");
        }
        JsonNode context = request.context();
        return request.hook().requiredContextFields().stream()
            .filter(field -> missing(context, field))
            .toList();
    }

    public static void requireCompleteContext(CdsHookRequest request) {
        List<String> missing = missingRequiredContextFields(request);
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_EVENT_001,
                "CDS Hooks 上下文缺少必填字段: " + String.join(",", missing));
        }
    }

    private static boolean missing(JsonNode context, String field) {
        if (context == null || context.isNull()) {
            return true;
        }
        JsonNode value = context.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return true;
        }
        return value.isTextual() && value.asText().isBlank();
    }
}
