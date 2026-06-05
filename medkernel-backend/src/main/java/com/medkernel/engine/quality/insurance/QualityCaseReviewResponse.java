package com.medkernel.engine.quality.insurance;

import com.medkernel.engine.evaluation.EvaluationModelStatus;

/**
 * 病案内涵质控响应。
 */
public record QualityCaseReviewResponse(
    String reviewId,
    CaseReviewStatus reviewStatus,
    String evaluationRunId,
    int resultCount,
    int findingCount,
    int taskCount,
    EvaluationModelStatus modelStatus,
    String modelDowngradeReason,
    String traceId
) {}
