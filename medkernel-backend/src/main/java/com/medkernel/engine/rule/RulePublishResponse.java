package com.medkernel.engine.rule;

import java.util.List;

/**
 * 规则发布出参（GA-ENG-API-05）：返回发布后的规则状态以及发布门禁所有测试用例的执行结果。
 */
public record RulePublishResponse(
    String ruleId,
    String versionId,
    RuleDefinitionStatus status,
    String traceId,
    List<RuleTestCaseResult> results,
    String impactDigest,
    String impactStatus,
    List<String> releaseEvidence
) {
    public RulePublishResponse(String ruleId,
                               String versionId,
                               RuleDefinitionStatus status,
                               String traceId,
                               List<RuleTestCaseResult> results) {
        this(ruleId, versionId, status, traceId, results, null, null, List.of());
    }

    public RulePublishResponse(String ruleId,
                               String versionId,
                               RuleDefinitionStatus status,
                               String traceId,
                               List<RuleTestCaseResult> results,
                               String impactDigest,
                               String impactStatus) {
        this(ruleId, versionId, status, traceId, results, impactDigest, impactStatus, List.of());
    }

    public RulePublishResponse {
        results = results == null ? List.of() : List.copyOf(results);
        releaseEvidence = releaseEvidence == null ? List.of() : List.copyOf(releaseEvidence);
    }
}
