package com.medkernel.engine.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 评估指标审核、灰度与全量发布说明。
 */
public record EvaluationIndicatorReleaseRequest(
    @NotBlank
    @Size(max = 500)
    String reason
) {}
