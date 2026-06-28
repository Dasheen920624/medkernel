package com.medkernel.engine.rule;

/**
 * 规则验证用例执行结果（GA-ENG-API-05）：对照期望命中/严重度与实际命中/严重度，记录通过状态与原因。
 */
public record RuleTestCaseResult(
    String caseId,
    RuleTestCaseType caseType,
    boolean expectedHit,
    boolean actualHit,
    RuleRiskLevel expectedSeverity,
    RuleRiskLevel actualSeverity,
    RuleTestCaseStatus status,
    String message
) {}
