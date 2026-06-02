package com.medkernel.engine.rule;

import java.util.List;

/**
 * 规则发布前影响分析响应。
 *
 * <p>{@code analysisStatus=PARTIAL} 表示当前仅能从规则库自身给出真实影响对象；
 * 缺少跨域反向索引的范围必须在 {@code unavailableScopes} 中明示。
 */
public record RuleImpactResponse(
    String ruleId,
    String versionId,
    RuleRiskLevel riskLevel,
    String analysisStatus,
    String impactDigest,
    List<RuleImpactObject> affectedRules,
    List<RuleImpactObject> affectedPathways,
    List<RuleImpactObject> inPathPatients,
    List<RuleImpactObject> syncTargets,
    List<String> unavailableScopes,
    String traceId
) {
    public RuleImpactResponse {
        affectedRules = affectedRules == null ? List.of() : List.copyOf(affectedRules);
        affectedPathways = affectedPathways == null ? List.of() : List.copyOf(affectedPathways);
        inPathPatients = inPathPatients == null ? List.of() : List.copyOf(inPathPatients);
        syncTargets = syncTargets == null ? List.of() : List.copyOf(syncTargets);
        unavailableScopes = unavailableScopes == null ? List.of() : List.copyOf(unavailableScopes);
    }
}
