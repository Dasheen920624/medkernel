package com.medkernel.engine.integration.fhir;

import java.math.BigDecimal;
import java.util.List;

import com.medkernel.engine.context.CanonicalResource;

/**
 * FHIR 资源转换为标准临床对象后的结果。
 */
public record CanonicalResourceMappingResult(
    CanonicalResource resource,
    List<FhirOperationOutcomeIssue> issues,
    BigDecimal mappingRate
) {
    public CanonicalResourceMappingResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
