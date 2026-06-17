package com.medkernel.engine.llm.eval;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AI 质量评测运行请求（OPT-06）。
 */
public record AiQualityEvalRunRequest(
    @NotBlank @Size(max = 64) String capabilityCode,
    @Size(max = 64) String providerCode,
    @NotBlank @Size(max = 128) String modelVersion,
    @NotBlank @Size(max = 128) String promptVersion,
    @NotBlank @Size(max = 128) String toolVersion,
    @Size(max = 1000) List<@NotNull @Valid AiQualityEvalCaseOutput> caseOutputs
) {
    public AiQualityEvalRunRequest {
        caseOutputs = caseOutputs == null ? List.of() : List.copyOf(caseOutputs);
    }
}
