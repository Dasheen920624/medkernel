package com.medkernel.engine.evaluation;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 评估结果中的质量问题请求。
 *
 * <p>P0/P1 必须携带责任科室和整改期限；P2/P3 只有在显式给出派单信息时才创建整改任务。
 */
public record QualityFindingRequest(
    @NotBlank String findingCode,
    @NotBlank String title,
    @NotBlank String description,
    @NotNull QualityFindingSeverity severity,
    @NotBlank String evidenceSummary,
    String responsibleDepartmentId,
    Instant dueAt,
    String assigneeUserId
) {}
