package com.medkernel.engine.llm.eval;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AI 质量评测单例输出（OPT-06）。
 *
 * <p>可承载 provider 实跑后的输出，也可承载 B0/离线夹具输出；引用字段用于真实性与幻觉拦截。
 */
public record AiQualityEvalCaseOutput(
    @NotNull Long caseId,
    @Size(max = 8000) String content,
    @Size(max = 128) String modelVersion,
    @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
    @Size(max = 4000) String sourceCitations
) {}
