package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 规则 DSL 评估结果值对象（GA-ENG-API-05）。
 *
 * <p>聚合是否命中、最高严重度、动作集合与解释快照，由 {@link RuleDslEvaluator} 产出，
 * 用于试运行、验证用例运行与真实执行的统一返回结构。
 */
public record RuleDslEvaluation(
    boolean hit,
    RuleRiskLevel severity,
    List<RuleActionResult> actions,
    JsonNode explanation
) {
    public RuleDslEvaluation {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
