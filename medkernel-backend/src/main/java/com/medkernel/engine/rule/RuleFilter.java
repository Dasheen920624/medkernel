package com.medkernel.engine.rule;

/**
 * 规则列表过滤条件（GA-ENG-API-05）：状态、类型、风险级别和关键词可选，{@code null} 视为不过滤。
 */
public record RuleFilter(
    RuleDefinitionStatus status,
    RuleType ruleType,
    RuleRiskLevel riskLevel,
    String keyword
) {}
