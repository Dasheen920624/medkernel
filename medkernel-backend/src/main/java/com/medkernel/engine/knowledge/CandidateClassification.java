package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 知识候选新旧识别结果，记录分类依据与对照摘要。
 */
@Table("mk_knowledge_candidate_classification")
public record CandidateClassification(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("org_path") String orgPath,
    @Column("identity_id") Long identityId,
    @Column("candidate_version_id") Long candidateVersionId,
    @Column("active_version_id") Long activeVersionId,
    @Column("classification") CandidateClassificationType classification,
    @Column("review_status") CandidateReviewStatus reviewStatus,
    @Column("content_hash") String contentHash,
    @Column("basis") String basis,
    @Column("diff_summary") String diffSummary,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
