package com.medkernel.engine.knowledge.production.initialization;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.versioning.VersionedAssetType;

/** 初始化发行批次条目。 */
@Table("mk_knowledge_initialization_item")
public record KnowledgeInitializationItem(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("batch_id") Long batchId,
    @Column("sequence_no") int sequenceNo,
    @Column("catalog_code") String catalogCode,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("canonical_id") String canonicalId,
    @Column("namespace") String namespace,
    @Column("asset_version") String assetVersion,
    @Column("source_version_id") Long sourceVersionId,
    @Column("source_hash") String sourceHash,
    @Column("candidate_ref") String candidateRef,
    @Column("candidate_classification_id") Long candidateClassificationId,
    @Column("candidate_content_hash") String candidateContentHash,
    @Column("risk_level") KnowledgeRiskLevel riskLevel,
    @Column("generated_by_model_flag") String generatedByModelFlag,
    @Column("dependencies_json") String dependenciesJson,
    @Column("governance_json") String governanceJson,
    @Column("change_type") InitializationChangeType changeType,
    @Column("replacement_canonical_id") String replacementCanonicalId,
    @Column("effective_to") Instant effectiveTo,
    @Column("status") KnowledgeInitializationItemStatus status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
