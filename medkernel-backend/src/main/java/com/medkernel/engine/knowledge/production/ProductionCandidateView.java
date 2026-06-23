package com.medkernel.engine.knowledge.production;

import java.time.Instant;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;

/**
 * 候选生产血缘视图：血缘行与审核归口决策。
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
