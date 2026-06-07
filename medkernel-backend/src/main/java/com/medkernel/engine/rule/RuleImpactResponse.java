package com.medkernel.engine.rule;

import java.util.List;

/**
 * 规则发布前影响分析响应。
 *
 * <p>{@code analysisStatus=COMPLETE} 表示路径、在径患者和集成适配器索引均已查询；
 * {@code PARTIAL} 表示当前运行时缺少某类索引，必须在 {@code unavailableScopes} 中明示。
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
    List<RuleImpactObject> integrationAdapters,
    List<String> unavailableScopes,
    String traceId
) {
    public RuleImpactResponse {
        affectedRules = affectedRules == null ? List.of() : List.copyOf(affectedRules);
        affectedPathways = affectedPathways == null ? List.of() : List.copyOf(affectedPathways);
        inPathPatients = inPathPatients == null ? List.of() : List.copyOf(inPathPatients);
        integrationAdapters = integrationAdapters == null ? List.of() : List.copyOf(integrationAdapters);
        unavailableScopes = unavailableScopes == null ? List.of() : List.copyOf(unavailableScopes);
    }
}
