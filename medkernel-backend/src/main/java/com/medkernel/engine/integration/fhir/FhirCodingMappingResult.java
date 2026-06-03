package com.medkernel.engine.integration.fhir;

import java.math.BigDecimal;
import java.util.List;

/**
 * FHIR Coding 经 TERM-01 字典评估后的状态。
 */
record FhirCodingMappingResult(
    String mappingStatus,
    List<FhirOperationOutcomeIssue> issues,
    BigDecimal mappingRate
) {
    FhirCodingMappingResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
