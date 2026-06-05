package com.medkernel.engine.quality.value;

/**
 * 价值指标数据源说明。
 *
 * @param sourceCode 源表或事实源代码
 * @param sourceName 面向客户的中文源名
 * @param status 当前源是否可用于本次聚合
 * @param evidence 口径证据或不可用原因
 */
public record ValueMetricDataSource(
    String sourceCode,
    String sourceName,
    ValueMetricStatus status,
    String evidence
) {}
