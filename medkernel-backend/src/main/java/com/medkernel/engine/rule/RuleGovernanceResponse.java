package com.medkernel.engine.rule;

import java.util.List;

/**
 * 规则知识治理快照，作为详情页与治理写操作的唯一状态出参。
 */
public record RuleGovernanceResponse(
    String ruleId,
    String versionId,
    RuleGovernanceState state,
    String authorId,
    String lastReason,
    List<RuleTestCaseResult> testResults,
    String impactDigest,
    String impactStatus,
    List<String> releaseEvidence,
    String traceId
) {
    public RuleGovernanceResponse {
        testResults = testResults == null ? List.of() : List.copyOf(testResults);
        releaseEvidence = releaseEvidence == null ? List.of() : List.copyOf(releaseEvidence);
    }
}
