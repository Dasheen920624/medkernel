package com.medkernel.engine.llm.eval;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 医学回归基准用例维护请求。
 */
public record MedicalRegressionCaseRequest(
    @NotBlank @Size(max = 64) String capabilityCode,
    @Size(max = 32) String caseDomain,
    @NotBlank @Size(max = 2000) String caseInput,
    @NotBlank @Size(max = 512) String expectedPhrase,
    @Size(max = 50) List<@NotBlank @Size(max = 128) String> expectedTerms,
    @Size(max = 50) List<@NotBlank @Size(max = 256) String> forbiddenAssertions,
    @Min(0) @Max(100) Integer minScore,
    @Size(max = 32) String redLineType,
    boolean citationRequired,
    @NotBlank @Size(max = 32) String caseVersion,
    @NotBlank @Size(max = 512) String sourceReference,
    Boolean enabled
) {
}
