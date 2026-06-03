package com.medkernel.engine.integration.fhir;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.ClinicalCodeMappingAnchor;
import com.medkernel.engine.context.TerminologyMappingPort;

/**
 * FHIR CodeableConcept/Coding 到 TERM-01 字典状态的确定性桥接。
 */
final class FhirTerminologyMapper {

    private static final BigDecimal FULL_RATE = new BigDecimal("1.0000");
    private static final BigDecimal PARTIAL_RATE = new BigDecimal("0.9000");

    private final TerminologyMappingPort terminology;

    FhirTerminologyMapper(TerminologyMappingPort terminology) {
        this.terminology = terminology;
    }

    FhirCodingMappingResult mapObservationCode(String tenantId,
                                               String observationId,
                                               String codeSystem,
                                               String code,
                                               String display,
                                               String sourceRecordId,
                                               String mappedVersion) {
        return mapCode(
            tenantId,
            CanonicalResourceType.OBSERVATION,
            observationId,
            "code",
            codeSystem,
            code,
            display,
            targetDictionaryForObservation(codeSystem),
            sourceRecordId,
            mappedVersion);
    }

    FhirCodingMappingResult mapCode(String tenantId,
                                    CanonicalResourceType resourceType,
                                    String resourceId,
                                    String fieldName,
                                    String codeSystem,
                                    String code,
                                    String display,
                                    String targetDictionary,
                                    String sourceRecordId,
                                    String mappedVersion) {
        ClinicalCodeMappingAnchor anchor = new ClinicalCodeMappingAnchor(
            resourceType,
            resourceId,
            fieldName,
            code,
            normalizeCodeSystem(codeSystem),
            display,
            targetDictionary,
            "FHIR",
            sourceRecordId,
            mappedVersion);
        Map<String, String> status = terminology.evaluate(tenantId, List.of(anchor));
        String mappingStatus = status.getOrDefault(anchor.key(), "UNKNOWN");
        if ("VALID".equals(mappingStatus)) {
            return new FhirCodingMappingResult(mappingStatus, List.of(), FULL_RATE);
        }
        String diagnostics = "TERM-01 未找到 "
            + firstNonBlank(codeSystem, "UNKNOWN_SYSTEM") + ":" + code
            + " 到 " + targetDictionary + " 的已确认映射，禁止字符近似兜底";
        return new FhirCodingMappingResult(
            mappingStatus,
            List.of(new FhirOperationOutcomeIssue("warning", "not-supported", diagnostics)),
            PARTIAL_RATE);
    }

    private static String targetDictionaryForObservation(String codeSystem) {
        String normalized = normalizeCodeSystem(codeSystem);
        return normalized.isBlank() || "LIS".equals(normalized) ? "LOINC" : normalized;
    }

    static String normalizeCodeSystem(String codeSystem) {
        if (codeSystem == null || codeSystem.isBlank()) {
            return "";
        }
        String trimmed = codeSystem.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("http://loinc.org".equals(lower) || "loinc".equals(lower)) {
            return "LOINC";
        }
        if (lower.startsWith("urn:local:")) {
            return trimmed.substring("urn:local:".length()).toUpperCase(Locale.ROOT);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
