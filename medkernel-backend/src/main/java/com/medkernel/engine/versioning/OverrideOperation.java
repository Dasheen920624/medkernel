package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 覆盖批量操作证据。
 */
@Table("mk_version_override_operation")
public record OverrideOperation(
    @Id Long id,
    @Column("operation_id") String operationId,
    @Column("tenant_id") String tenantId,
    @Column("operation_type") OverrideOperationType operationType,
    @Column("template_id") String templateId,
    @Column("source_org_unit_id") String sourceOrgUnitId,
    @Column("target_org_units_json") String targetOrgUnitsJson,
    OverrideOperationStatus status,
    @Column("preview_digest") String previewDigest,
    @Column("result_summary_json") String resultSummaryJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {
    public OverrideOperation withStatus(
            OverrideOperationStatus newStatus,
            String newResultSummaryJson) {
        return new OverrideOperation(
            id,
            operationId,
            tenantId,
            operationType,
            templateId,
            sourceOrgUnitId,
            targetOrgUnitsJson,
            newStatus,
            previewDigest,
            newResultSummaryJson,
            createdAt,
            createdBy,
            traceId
        );
    }
}
