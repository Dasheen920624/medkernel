package com.medkernel.engine.knowledge.production;

import java.time.Instant;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;

/**
 * 候选生产来源溯源视图（AIK-STD-12，FR-2/4）：审核台候选经血缘反查 AI 工厂来源。
 *
 * <p>{@code aiGenerated = producer ≠ MANUAL}——用于审核台 AI 标识；附归属 job / 生产器 / 管道 / 模型策略 /
 * 领域 / 风险 / 生产时点与人 / 模型三元组 / 置信与降级证据，供来源溯源。无血缘行的候选（手建）不产此视图（铁律 #1 不臆造）。
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
    String producedBy,
    String modelTaskId,
    String modelMode,
    String modelVersion,
    String promptVersion,
    String toolVersion,
    String sourceCitations,
    Double confidence,
    Boolean fallbackUsed,
    String fallbackReason
) {
    public static CandidateProvenanceView from(KnowledgeProductionCandidate row, KnowledgeProductionJob job) {
        return from(row, job, ExplainEvidence.empty());
    }

    public static CandidateProvenanceView from(
            KnowledgeProductionCandidate row,
            KnowledgeProductionJob job,
            ExplainEvidence explain) {
        return new CandidateProvenanceView(
            row.candidateRef(), job.producer() != KnowledgeProducer.MANUAL, job.producer(),
            job.jobCode(), job.targetPipeline(), job.domain(), job.modelStrategy(),
            row.riskLevel(), row.createdAt(), row.createdBy(),
            explain.modelTaskId(), explain.modelMode(), explain.modelVersion(), explain.promptVersion(),
            explain.toolVersion(), explain.sourceCitations(), explain.confidence(), explain.fallbackUsed(),
            explain.fallbackReason());
    }

    public record ExplainEvidence(
        String modelTaskId,
        String modelMode,
        String modelVersion,
        String promptVersion,
        String toolVersion,
        String sourceCitations,
        Double confidence,
        Boolean fallbackUsed,
        String fallbackReason
    ) {
        static ExplainEvidence empty() {
            return new ExplainEvidence(null, null, null, null, null, null, null, null, null);
        }
    }
}
