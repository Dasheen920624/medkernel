package com.medkernel.engine.integration.fhir;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 标准临床对象转换为 FHIR 资源后的结果。
 */
public record FhirResourceMappingResult(
    JsonNode resource,
    List<FhirOperationOutcomeIssue> issues,
    BigDecimal mappingRate
) {
    public FhirResourceMappingResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
