package com.medkernel.engine.llm.eval;

import jakarta.validation.constraints.NotBlank;

/**
 * 运行医学回归评测请求（LLM-07 T18）：对候选 provider 的指定模型版本在某能力码基准集上跑评测。
 */
public record ModelEvalRunRequest(
    @NotBlank String providerCode,
    @NotBlank String modelVersion,
    @NotBlank String capabilityCode
) {}
