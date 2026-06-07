package com.medkernel.engine.rule;

import java.util.List;

import com.medkernel.engine.versioning.AssetVersionStatus;

/**
 * 规则详情出参（GA-ENG-API-05）：聚合规则定义、当前版本、统一部署状态与该版本下全部测试用例。
 */
public record RuleDetailResponse(
    RuleDefinition definition,
    RuleVersion version,
    List<RuleTestCase> testCases,
    AssetVersionStatus deploymentStatus,
    RuleGovernanceResponse governance
) {
    public RuleDetailResponse {
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
    }
}
