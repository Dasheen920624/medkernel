package com.medkernel.engine.authoring;

import java.util.List;

import com.medkernel.engine.rule.RuleRiskLevel;

/**
 * 批量发布影响分析中的单规则结果。
 */
public record AuthoringBatchRuleImpactItem(
    String ruleId,
    String versionId,
    RuleRiskLevel riskLevel,
    String analysisStatus,
    String impactDigest,
    int affectedCount,
    List<String> unavailableScopes
) {
    public AuthoringBatchRuleImpactItem {
        unavailableScopes = unavailableScopes == null ? List.of() : List.copyOf(unavailableScopes);
    }
}
