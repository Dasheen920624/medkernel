package com.medkernel.engine.rule;

/**
 * 新增规则验证用例的出参（GA-ENG-API-05）：返回新用例 ID、类型与初始执行状态。
 */
public record RuleTestCaseResponse(
    String caseId,
    RuleTestCaseType caseType,
    RuleTestCaseStatus lastStatus
) {}
