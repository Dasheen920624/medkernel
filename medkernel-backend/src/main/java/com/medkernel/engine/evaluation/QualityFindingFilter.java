package com.medkernel.engine.evaluation;

/**
 * 质量问题分页查询过滤条件。
 *
 * <p>按问题严重度、闭环状态和责任科室组合过滤；字段为 {@code null} 时表示不过滤。
 */
public record QualityFindingFilter(
    QualityFindingSeverity severity,
    QualityFindingStatus status,
    String responsibleDepartmentId
) {}
