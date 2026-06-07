package com.medkernel.engine.rule;

import java.util.List;

/**
 * 规则知识治理快照，作为详情页与治理写操作的唯一状态出参。
 */
public record RuleGovernanceResponse(
    String ruleId,
    String versionId,
    RuleGovernanceState state,
    int requiredSignoffs,
    int reviewRound,
    int committeeApprovalCount,
    String authorId,
    String lastReason,
    List<RuleSignoff> signoffs,
    List<RuleTestCaseResult> testResults,
    String impactDigest,
    String impactStatus,
    List<String> releaseEvidence,
    String traceId
) {
    public RuleGovernanceResponse {
        signoffs = signoffs == null ? List.of() : List.copyOf(signoffs);
        testResults = testResults == null ? List.of() : List.copyOf(testResults);
        releaseEvidence = releaseEvidence == null ? List.of() : List.copyOf(releaseEvidence);
    }
}
