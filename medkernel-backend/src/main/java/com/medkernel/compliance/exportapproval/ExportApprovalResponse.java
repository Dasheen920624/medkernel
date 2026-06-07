package com.medkernel.compliance.exportapproval;

import java.time.Instant;

/**
 * SYS-06 敏感数据导出审批响应。
 */
public record ExportApprovalResponse(
    String approvalId,
    String resourceType,
    String exportScopeSnapshot,
    String idempotencyKey,
    String requestReason,
    ExportApprovalStatus status,
    String requestedBy,
    String reviewerId,
    String reviewDecision,
    String reviewComment,
    String approvalEvidenceId,
    String approvalEvidenceFileUri,
    String exportUri,
    String exportDigest,
    String exportEvidenceId,
    String exportEvidenceFileUri,
    Long version,
    Instant requestedAt,
    Instant reviewedAt
) {

    public static ExportApprovalResponse from(ExportApproval approval) {
        return new ExportApprovalResponse(
            approval.approvalId(),
            approval.resourceType(),
            approval.exportScopeSnapshot(),
            approval.idempotencyKey(),
            approval.requestReason(),
            ExportApprovalStatus.valueOf(approval.status()),
            approval.requestedBy(),
            approval.reviewerId(),
            approval.reviewDecision(),
            approval.reviewComment(),
            approval.approvalEvidenceId(),
            approval.approvalEvidenceFileUri(),
            approval.exportUri(),
            approval.exportDigest(),
            approval.exportEvidenceId(),
            approval.exportEvidenceFileUri(),
            approval.version(),
            approval.requestedAt(),
            approval.reviewedAt());
    }
}
