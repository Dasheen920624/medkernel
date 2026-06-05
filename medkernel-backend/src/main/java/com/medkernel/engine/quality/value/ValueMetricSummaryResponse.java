package com.medkernel.engine.quality.value;

import java.util.List;

/**
 * OPT-08 价值指标聚合响应。
 */
public record ValueMetricSummaryResponse(
    List<ValueMetricResponse> metrics
) {
    public ValueMetricSummaryResponse {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }
}
