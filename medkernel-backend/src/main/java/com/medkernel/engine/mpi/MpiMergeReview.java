package com.medkernel.engine.mpi;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 高危患者主索引合并审核单。
 */
@Table("mk_mpi_merge_review")
public record MpiMergeReview(
    @Id
    @Column("review_id")
    String reviewId,
    @Column("tenant_id")
    String tenantId,
    @Column("source_mpi_id")
    String sourceMpiId,
    @Column("target_mpi_id")
    String targetMpiId,
    @Column("risk_level")
    String riskLevel,
    @Column("status")
    String status,
    @Column("risk_reason")
    String riskReason,
    @Column("requested_by")
    String requestedBy,
    @Column("requested_at")
    Instant requestedAt,
    @Column("reviewed_by")
    String reviewedBy,
    @Column("reviewed_at")
    Instant reviewedAt,
    @Column("review_reason")
    String reviewReason,
    @Column("created_at")
    Instant createdAt,
    @Column("created_by")
    String createdBy,
    @Column("updated_at")
    Instant updatedAt,
    @Column("updated_by")
    String updatedBy,
    @Column("trace_id")
    String traceId
) {

    public static MpiMergeReview pending(String reviewId,
                                         String tenantId,
                                         String sourceMpiId,
                                         String targetMpiId,
                                         String riskLevel,
                                         String riskReason,
                                         String requestedBy,
                                         Instant now,
                                         String traceId) {
        return new MpiMergeReview(
            reviewId,
            tenantId,
            sourceMpiId,
            targetMpiId,
            riskLevel,
            "PENDING",
            riskReason,
            requestedBy,
            now,
            null,
            null,
            null,
            now,
            requestedBy,
            now,
            requestedBy,
            traceId
        );
    }

    public MpiMergeReview confirmed(String reviewer, String reason, Instant now) {
        return new MpiMergeReview(
            reviewId,
            tenantId,
            sourceMpiId,
            targetMpiId,
            riskLevel,
            "CONFIRMED",
            riskReason,
            requestedBy,
            requestedAt,
            reviewer,
            now,
            reason,
            createdAt,
            createdBy,
            now,
            reviewer,
            traceId
        );
    }
}
