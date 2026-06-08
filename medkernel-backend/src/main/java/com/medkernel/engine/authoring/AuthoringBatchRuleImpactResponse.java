package com.medkernel.engine.authoring;

import java.util.List;

/**
 * 规则批量发布聚合影响分析响应。
 */
public record AuthoringBatchRuleImpactResponse(
    int totalCount,
    int highRiskCount,
    int criticalRiskCount,
    List<AuthoringBatchRuleImpactItem> items,
    String traceId
) {
    public AuthoringBatchRuleImpactResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
