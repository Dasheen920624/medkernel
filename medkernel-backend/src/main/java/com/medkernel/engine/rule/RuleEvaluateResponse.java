package com.medkernel.engine.rule;

import java.util.List;

import com.medkernel.engine.cdshook.CdsHookCard;

/**
 * 规则真实执行出参（GA-ENG-API-05）：汇总命中项、最高严重度与 traceId，供上游临床嵌入提示使用。
 */
public record RuleEvaluateResponse(
    String requestId,
    List<RuleEvaluationItem> items,
    RuleRiskLevel highestSeverity,
    List<CdsHookCard> cards,
    String traceId
) {
    public RuleEvaluateResponse {
        items = items == null ? List.of() : List.copyOf(items);
        cards = cards == null ? List.of() : List.copyOf(cards);
    }
}
