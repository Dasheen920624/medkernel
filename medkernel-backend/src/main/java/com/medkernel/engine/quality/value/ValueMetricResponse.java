package com.medkernel.engine.quality.value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * OPT-08 单个价值指标响应。
 *
 * @param id 下钻用稳定 ID，等同于 metricCode
 * @param metricCode 指标口径代码
 * @param displayName 中文展示名
 * @param formula 中文公式说明
 * @param formulaVersion 公式版本
 * @param status 指标本次是否可计算
 * @param numerator 分子事实数
 * @param denominator 分母事实数
 * @param value 指标值；比例类为 0-1，计数类为事实数
 * @param unit 单位
 * @param dataSources 数据源与证据
 * @param explanation 计算解释或缺源原因
 * @param calculatedAt 计算时间
 */
public record ValueMetricResponse(
    String id,
    ValueMetricCode metricCode,
    String displayName,
    String formula,
    String formulaVersion,
    ValueMetricStatus status,
    BigDecimal numerator,
    BigDecimal denominator,
    BigDecimal value,
    String unit,
    List<ValueMetricDataSource> dataSources,
    String explanation,
    Instant calculatedAt
) {
    public ValueMetricResponse {
        dataSources = dataSources == null ? List.of() : List.copyOf(dataSources);
    }
}
