package com.medkernel.engine.integration.fhir;

/**
 * FHIR OperationOutcome issue 的最小内核表示。
 */
public record FhirOperationOutcomeIssue(
    String severity,
    String code,
    String diagnostics
) {}
