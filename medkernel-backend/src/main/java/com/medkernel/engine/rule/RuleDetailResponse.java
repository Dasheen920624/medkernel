package com.medkernel.engine.rule;

import java.util.List;

import com.medkernel.engine.versioning.AssetTriggerBinding;
import com.medkernel.engine.versioning.AssetVersionStatus;

/**
 * 规则详情出参（GA-ENG-API-05）：聚合规则定义、当前版本、版本历史、统一部署状态与该版本下全部验证用例。
 */
public record RuleDetailResponse(
    RuleDefinition definition,
    RuleVersion version,
    List<RuleVersion> versions,
    List<RuleTestCase> testCases,
    List<AssetTriggerBinding> triggerBindings,
    AssetVersionStatus deploymentStatus,
    RuleGovernanceResponse governance
) {
    public RuleDetailResponse {
        versions = versions == null ? List.of() : List.copyOf(versions);
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
        triggerBindings = triggerBindings == null ? List.of() : List.copyOf(triggerBindings);
    }
}
