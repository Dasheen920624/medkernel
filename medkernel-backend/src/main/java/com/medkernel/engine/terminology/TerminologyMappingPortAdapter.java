package com.medkernel.engine.terminology;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.ClinicalCodeMappingAnchor;
import com.medkernel.engine.context.TerminologyMappingPort;
import com.medkernel.engine.versioning.PlatformAuthority;

/**
 * 医院运行修订锁定术语版本对标准上下文 / FHIR 门面的字典状态端口实现。
 */
@Component
public class TerminologyMappingPortAdapter implements TerminologyMappingPort {

    private final StandardTermRepository standardTerms;
    private final EffectiveTermMappingResolver effectiveMappings;

    public TerminologyMappingPortAdapter(
            StandardTermRepository standardTerms,
            EffectiveTermMappingResolver effectiveMappings) {
        this.standardTerms = standardTerms;
        this.effectiveMappings = effectiveMappings;
    }

    @Override
    public Map<String, String> evaluate(
            String tenantId,
            String runtimeReleaseId,
            List<ClinicalCodeMappingAnchor> anchors) {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (ClinicalCodeMappingAnchor anchor : anchors == null ? List.<ClinicalCodeMappingAnchor>of() : anchors) {
            statuses.putIfAbsent(
                anchor.key(),
                evaluateAnchor(tenantId, runtimeReleaseId, anchor));
        }
        return statuses;
    }

    private String evaluateAnchor(
            String tenantId,
            String runtimeReleaseId,
            ClinicalCodeMappingAnchor anchor) {
        String targetDictionary = normalize(anchor.targetDictionaryKey());
        String sourceSystem = normalize(anchor.localCodeSystem());
        List<String> standardSources = standardTermSources(tenantId);
        if (!targetDictionary.isBlank() && targetDictionary.equals(sourceSystem)) {
            return standardTerms.findFirstByTenantIdsAndStandardSystemAndTermCodeAndStatus(
                    standardSources, tenantId, targetDictionary, anchor.localCode(), StandardTermStatus.ACTIVE)
                .isPresent() ? "VALID" : "UNKNOWN";
        }
        List<EffectiveTermMapping> confirmed = effectiveMappings.resolve(
            tenantId,
            runtimeReleaseId,
            sourceSystem.isBlank() ? null : sourceSystem,
            anchor.localCode(),
            targetDictionary.isBlank() ? null : targetDictionary,
            category(anchor.resourceType()));
        if (confirmed.size() == 1) {
            return "VALID";
        }
        return confirmed.isEmpty() ? "UNKNOWN" : "PARTIAL";
    }

    private static String category(CanonicalResourceType resourceType) {
        if (resourceType == null) {
            return null;
        }
        return switch (resourceType) {
            case CONDITION -> TermCategory.DIAGNOSIS.name();
            case OBSERVATION, NURSING_ASSESSMENT, DIAGNOSTIC_REPORT -> TermCategory.LAB.name();
            case MEDICATION -> TermCategory.DRUG.name();
            case PROCEDURE -> TermCategory.PROCEDURE.name();
            default -> null;
        };
    }

    private static List<String> standardTermSources(String tenantId) {
        String current = tenantId == null ? "" : tenantId.trim();
        if (PlatformAuthority.PLATFORM_TENANT_ID.equals(current)) {
            return List.of(PlatformAuthority.PLATFORM_TENANT_ID);
        }
        if (current.isBlank()) {
            return List.of(PlatformAuthority.PLATFORM_TENANT_ID);
        }
        return List.of(PlatformAuthority.PLATFORM_TENANT_ID, current);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("http://loinc.org".equals(lower) || "loinc".equals(lower)) {
            return "LOINC";
        }
        if (lower.startsWith("urn:local:")) {
            return trimmed.substring("urn:local:".length()).toUpperCase(Locale.ROOT);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
