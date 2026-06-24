package com.medkernel.compliance.exportconfirmation;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 敏感数据导出确认实体。
 */
@Table("mk_compliance_export_confirmation")
public record ExportConfirmation(
    @Id Long id,
    @Column("confirmation_id") String confirmationId,
    @Column("tenant_id") String tenantId,
    @Column("resource_type") String resourceType,
    @Column("export_scope_snapshot") String exportScopeSnapshot,
    @Column("idempotency_key") String idempotencyKey,
    @Column("reason") String reason,
    @Column("confirmed_by") String confirmedBy,
    @Column("confirmed_at") Instant confirmedAt,
    @Column("status") String status,
    @Column("export_uri") String exportUri,
    @Column("export_digest") String exportDigest,
    @Column("confirmation_evidence_id") String confirmationEvidenceId,
    @Column("confirmation_evidence_file_uri") String confirmationEvidenceFileUri,
    @Column("export_evidence_id") String exportEvidenceId,
    @Column("export_evidence_file_uri") String exportEvidenceFileUri,
    @Column("version") Long version,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {

    public ExportConfirmation withId(Long newId) {
        return new ExportConfirmation(
            newId,
            confirmationId,
            tenantId,
            resourceType,
            exportScopeSnapshot,
            idempotencyKey,
            reason,
            confirmedBy,
            confirmedAt,
            status,
            exportUri,
            exportDigest,
            confirmationEvidenceId,
            confirmationEvidenceFileUri,
            exportEvidenceId,
            exportEvidenceFileUri,
            version,
            createdAt,
            createdBy,
            updatedAt,
            updatedBy,
            traceId
        );
    }
}
