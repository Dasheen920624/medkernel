package com.medkernel.engine.knowledge.production;

import java.time.Instant;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;

/**
 * 候选生产来源溯源视图（AIK-STD-12 PR1，FR-2/4）：审核台候选经血缘反查 AI 工厂来源。
 *
 * <p>{@code aiGenerated = producer ≠ MANUAL}——用于审核台 AI 标识；附归属 job / 生产器 / 管道 / 模型策略 /
 * 领域 / 风险 / 生产时点与人，供来源溯源。无血缘行的候选（手建）不产此视图（铁律 #1 不臆造）。
 */
public record CandidateProvenanceView(
    String candidateRef,
    boolean aiGenerated,
    KnowledgeProducer producer,
    String jobCode,
    TargetPipeline targetPipeline,
    KnowledgeDomain domain,
    String modelStrategy,
    KnowledgeRiskLevel riskLevel,
    Instant producedAt,
    String producedBy
) {
    public static CandidateProvenanceView from(KnowledgeProductionCandidate row, KnowledgeProductionJob job) {
        return new CandidateProvenanceView(
            row.candidateRef(), job.producer() != KnowledgeProducer.MANUAL, job.producer(),
            job.jobCode(), job.targetPipeline(), job.domain(), job.modelStrategy(),
            row.riskLevel(), row.createdAt(), row.createdBy());
    }
}
