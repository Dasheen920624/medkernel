package com.medkernel.engine.cdss.risk;

import java.util.List;

/**
 * CDSS 风险矩阵查询 / 更新响应。
 */
public record CdssRiskMatrixResponse(
    List<CdssRiskMatrixRule> rules,
    String traceId
) {
    public CdssRiskMatrixResponse {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
