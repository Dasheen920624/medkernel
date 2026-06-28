package com.medkernel.engine.rule;

import java.util.List;

/**
 * 规则验证用例执行响应。
 */
public record RuleTestRunResponse(
    String ruleId,
    String versionId,
    boolean allPassed,
    List<RuleTestCaseResult> results,
    String traceId
) {
    public RuleTestRunResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
