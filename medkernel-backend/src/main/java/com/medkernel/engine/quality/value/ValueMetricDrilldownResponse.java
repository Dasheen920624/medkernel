package com.medkernel.engine.quality.value;

import java.util.List;

/**
 * OPT-08 价值指标下钻响应。
 */
public record ValueMetricDrilldownResponse(
    ValueMetricResponse metric,
    List<ValueMetricDrilldownItem> items,
    int offset,
    int limit,
    long total,
    boolean hasNext
) {
    public ValueMetricDrilldownResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
