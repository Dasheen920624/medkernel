package com.medkernel.engine.knowledge.production.initialization;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 知识初始化发行批次。 */
@Table("mk_knowledge_initialization_batch")
public record KnowledgeInitializationBatch(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("batch_code") String batchCode,
    @Column("release_type") InitializationReleaseType releaseType,
    @Column("release_version") String releaseVersion,
    @Column("foundation_release_version") String foundationReleaseVersion,
    @Column("phase_code") InitializationPhase phase,
    @Column("status") KnowledgeInitializationBatchStatus status,
    @Column("source_manifest_hash") String sourceManifestHash,
    @Column("candidate_manifest_hash") String candidateManifestHash,
    @Column("overall_hash") String overallHash,
    @Column("source_count") int sourceCount,
    @Column("candidate_count") int candidateCount,
    @Column("low_count") int lowCount,
    @Column("medium_count") int mediumCount,
    @Column("high_count") int highCount,
    @Column("coverage_json") String coverageJson,
    @Column("template_version") String templateVersion,
    @Column("model_version") String modelVersion,
    @Column("summary") String summary,
    @Column("idempotency_key") String idempotencyKey,
    @Column("last_bulk_idempotency_key") String lastBulkIdempotencyKey,
    @Column("last_bulk_at") Instant lastBulkAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
