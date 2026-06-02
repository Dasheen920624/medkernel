package com.medkernel.engine.terminology;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 本地术语→标准术语映射候选项（待人工或规则确认）。
 *
 * <p>由规则引擎、AI、手工录入或批量导入产生（{@link MappingCandidateSource}），
 * 经审核后由 {@link TerminologyService#confirmCandidate} 升级为正式 {@link TermMapping}；
 * 状态字段 {@link MappingCandidateStatus} 反映审核流转（PENDING/CONFIRMED/REJECTED/EXPIRED）。
 */
@Table("mapping_candidate")
public record MappingCandidate(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("local_term_id") Long localTermId,
    @Column("standard_term_id") Long standardTermId,
    @Column("confidence") Double confidence,
    @Column("candidate_source") MappingCandidateSource candidateSource,
    @Column("risk_level") TermRiskLevel riskLevel,
    @Column("evidence_text") String evidenceText,
    @Column("conflict_flag") Boolean conflictFlag,
    @Column("status") MappingCandidateStatus status,
    @Column("review_note") String reviewNote,
    @Column("reviewed_by") String reviewedBy,
    @Column("reviewed_at") Instant reviewedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    MappingCandidate confirmed(String note, String userId, Instant now) {
        return new MappingCandidate(
            id, tenantId, localTermId, standardTermId, confidence, candidateSource,
            riskLevel, evidenceText, conflictFlag, MappingCandidateStatus.CONFIRMED,
            note, userId, now, createdAt, createdBy, now, userId
        );
    }

    MappingCandidate withConflictFlag(boolean nextConflictFlag, String userId, Instant now) {
        return new MappingCandidate(
            id, tenantId, localTermId, standardTermId, confidence, candidateSource,
            riskLevel, evidenceText, nextConflictFlag, status,
            reviewNote, reviewedBy, reviewedAt, createdAt, createdBy, now, userId
        );
    }
}
