package com.medkernel.engine.evaluation;

/**
 * 整改报告过滤条件。
 *
 * <p>当前服务按租户必选隔离，可选按责任科室聚合。
 */
public record RectificationReportFilter(
    String responsibleDepartmentId
) {}
