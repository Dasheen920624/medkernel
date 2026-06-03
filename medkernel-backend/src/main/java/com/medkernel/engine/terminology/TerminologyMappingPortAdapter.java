package com.medkernel.engine.terminology;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.ClinicalCodeMappingAnchor;
import com.medkernel.engine.context.TerminologyMappingPort;

/**
 * TERM-01 已确认映射包对标准上下文 / FHIR 门面的字典状态端口实现。
 */
@Component
public class TerminologyMappingPortAdapter implements TerminologyMappingPort {

    private final StandardTermRepository standardTerms;
    private final TermMappingRepository mappings;

    public TerminologyMappingPortAdapter(StandardTermRepository standardTerms, TermMappingRepository mappings) {
        this.standardTerms = standardTerms;
        this.mappings = mappings;
    }

    @Override
    public Map<String, String> evaluate(String tenantId, List<ClinicalCodeMappingAnchor> anchors) {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (ClinicalCodeMappingAnchor anchor : anchors == null ? List.<ClinicalCodeMappingAnchor>of() : anchors) {
            statuses.putIfAbsent(anchor.key(), evaluateAnchor(tenantId, anchor));
        }
        return statuses;
    }

    private String evaluateAnchor(String tenantId, ClinicalCodeMappingAnchor anchor) {
        String targetDictionary = normalize(anchor.targetDictionaryKey());
        String sourceSystem = normalize(anchor.localCodeSystem());
        if (!targetDictionary.isBlank() && targetDictionary.equals(sourceSystem)) {
            return standardTerms.findByTenantIdAndStandardSystemAndTermCodeAndStatus(
                    tenantId, targetDictionary, anchor.localCode(), StandardTermStatus.ACTIVE)
                .isPresent() ? "VALID" : "UNKNOWN";
        }
        List<TermMapping> confirmed = mappings.findConfirmedByTenantIdAndAnchor(
            tenantId,
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
