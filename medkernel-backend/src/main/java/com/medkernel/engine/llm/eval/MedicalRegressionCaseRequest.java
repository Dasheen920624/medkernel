package com.medkernel.engine.llm.eval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 医学回归基准用例维护请求。
 */
public record MedicalRegressionCaseRequest(
    @NotBlank @Size(max = 64) String capabilityCode,
    @NotBlank @Size(max = 2000) String caseInput,
    @NotBlank @Size(max = 512) String expectedPhrase,
    @Size(max = 32) String redLineType,
    boolean citationRequired,
    @NotBlank @Size(max = 32) String caseVersion,
    @NotBlank @Size(max = 512) String sourceReference,
    Boolean enabled
) {
}
