package com.medkernel.compliance.exportconfirmation;

import java.time.Instant;

/**
 * 敏感数据导出确认响应。
 */
public record ExportConfirmationResponse(
    String confirmationId,
    String resourceType,
    String exportScopeSnapshot,
    String idempotencyKey,
    String reason,
    ExportConfirmationStatus status,
    String confirmedBy,
    String confirmationEvidenceId,
    String confirmationEvidenceFileUri,
    String exportUri,
    String exportDigest,
    String exportEvidenceId,
    String exportEvidenceFileUri,
    Long version,
    Instant confirmedAt
) {

    public static ExportConfirmationResponse from(ExportConfirmation confirmation) {
        return new ExportConfirmationResponse(
            confirmation.confirmationId(),
            confirmation.resourceType(),
            confirmation.exportScopeSnapshot(),
            confirmation.idempotencyKey(),
            confirmation.reason(),
            ExportConfirmationStatus.valueOf(confirmation.status()),
            confirmation.confirmedBy(),
            confirmation.confirmationEvidenceId(),
            confirmation.confirmationEvidenceFileUri(),
            confirmation.exportUri(),
            confirmation.exportDigest(),
            confirmation.exportEvidenceId(),
            confirmation.exportEvidenceFileUri(),
            confirmation.version(),
            confirmation.confirmedAt()
        );
    }
}
