package com.medkernel.engine.llm.eval;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 医学回归独立专家复核签字请求。 */
public record ModelEvalSignOffRequest(
    @NotNull
    @AssertTrue(message = "必须确认已逐例核查评测证据")
    Boolean evidenceAcknowledged,

    @NotBlank
    @Size(min = 10, max = 1000)
    String reviewComment
) {
}
