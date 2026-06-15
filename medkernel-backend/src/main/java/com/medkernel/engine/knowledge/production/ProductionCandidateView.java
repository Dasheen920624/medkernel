package com.medkernel.engine.knowledge.production;

import java.time.Instant;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;

/**
 * 候选生产血缘视图（AIK-STD-13 PR3，FR-5/6 可回溯）：血缘行 + 会签路由决策（只读计算）。
 */
public record ProductionCandidateView(
    String jobCode,
    String assetIdentity,
    String contentHash,
    String candidateRef,
    KnowledgeRiskLevel riskLevel,
    Instant createdAt,
    String createdBy,
    ReviewRoutingDecision routing
) {
    public static ProductionCandidateView from(KnowledgeProductionCandidate row, ReviewRoutingDecision routing) {
        return new ProductionCandidateView(row.jobCode(), row.assetIdentity(), row.contentHash(),
            row.candidateRef(), row.riskLevel(), row.createdAt(), row.createdBy(), routing);
    }
}
