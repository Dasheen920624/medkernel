package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 知识候选审核分派与结论记录。
 */
@Table("mk_knowledge_review_assignment")
public record ReviewAssignment(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("org_path") String orgPath,
    @Column("candidate_classification_id") Long candidateClassificationId,
    @Column("identity_id") Long identityId,
    @Column("candidate_version_id") Long candidateVersionId,
    @Column("assigned_to") String assignedTo,
    @Column("review_status") CandidateReviewStatus reviewStatus,
    @Column("decision") KnowledgeCandidateReviewDecision decision,
    @Column("reason") String reason,
    @Column("decided_by") String decidedBy,
    @Column("decided_at") Instant decidedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
