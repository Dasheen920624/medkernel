package com.medkernel.engine.rule;

import java.time.Instant;

/**
 * 规则执行目录条目，仅返回回放选择和状态识别所需的非敏感摘要。
 */
public record RuleExecutionSummaryResponse(
    String executionId,
    String ruleId,
    String versionId,
    String triggerPoint,
    boolean hit,
    RuleRiskLevel severity,
    RuleExecutionStatus status,
    Instant executedAt,
    String traceId
) {
    static RuleExecutionSummaryResponse from(RuleExecutionLog execution) {
        return new RuleExecutionSummaryResponse(
            execution.executionId(),
            execution.ruleId(),
            execution.versionId(),
            execution.triggerPoint(),
            Boolean.TRUE.equals(execution.hit()),
            execution.severity(),
            execution.status(),
            execution.executedAt(),
            execution.traceId()
        );
    }
}
