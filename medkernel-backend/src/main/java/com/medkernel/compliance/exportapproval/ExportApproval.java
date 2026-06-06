package com.medkernel.compliance.exportapproval;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * SYS-06 敏感数据导出审批实体。
 */
@Table("mk_compliance_export_approval")
public record ExportApproval(
    @Id Long id,
    @Column("approval_id") String approvalId,
    @Column("tenant_id") String tenantId,
    @Column("resource_type") String resourceType,
    @Column("export_scope_snapshot") String exportScopeSnapshot,
    @Column("idempotency_key") String idempotencyKey,
    @Column("request_reason") String requestReason,
    @Column("requested_by") String requestedBy,
    @Column("requested_at") Instant requestedAt,
    @Column("status") String status,
    @Column("reviewer_id") String reviewerId,
    @Column("review_decision") String reviewDecision,
    @Column("review_comment") String reviewComment,
    @Column("reviewed_at") Instant reviewedAt,
    @Column("export_uri") String exportUri,
    @Column("export_digest") String exportDigest,
    @Column("approval_evidence_id") String approvalEvidenceId,
    @Column("approval_evidence_file_uri") String approvalEvidenceFileUri,
    @Column("export_evidence_id") String exportEvidenceId,
    @Column("export_evidence_file_uri") String exportEvidenceFileUri,
    @Column("version") Long version,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {

    public ExportApproval withId(Long newId) {
        return new ExportApproval(newId, approvalId, tenantId, resourceType, exportScopeSnapshot, idempotencyKey,
            requestReason, requestedBy, requestedAt, status, reviewerId, reviewDecision, reviewComment, reviewedAt,
            exportUri, exportDigest, approvalEvidenceId, approvalEvidenceFileUri, exportEvidenceId,
            exportEvidenceFileUri, version, createdAt, createdBy, updatedAt, updatedBy, traceId);
    }
}
