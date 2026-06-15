package com.medkernel.engine.knowledge.production;

import java.time.Instant;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 知识生产 job 视图（AIK-STD-13）。
 */
public record ProductionJobResponse(
    String jobCode,
    String tenantId,
    String sourceScope,
    VersionedAssetType assetType,
    KnowledgeProducer producer,
    TargetPipeline targetPipeline,
    String modelStrategy,
    ProductionJobStatus status,
    int candidateCount,
    Instant createdAt
) {

    public static ProductionJobResponse from(KnowledgeProductionJob job) {
        return new ProductionJobResponse(job.jobCode(), job.tenantId(), job.sourceScope(), job.assetType(),
            job.producer(), job.targetPipeline(), job.modelStrategy(), job.status(), job.candidateCount(),
            job.createdAt());
    }
}
