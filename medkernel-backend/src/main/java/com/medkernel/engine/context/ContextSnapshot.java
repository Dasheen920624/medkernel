package com.medkernel.engine.context;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 标准上下文聚合根。
 *
 * <p>对齐 V7 表 {@code context_snapshot}；快照一经创建即不可变。
 */
@Table("context_snapshot")
public record ContextSnapshot(
    @Id Long id,
    @Column("snapshot_id") String snapshotId,
    @Column("tenant_id") String tenantId,
    @Column("org_unit_id") String orgUnitId,
    @Column("request_id") String requestId,
    @Column("org_path") String orgPath,
    @Column("package_version") String packageVersion,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("knowledge_pkg_version") String knowledgePackageVersion,
    @Column("rule_pkg_version") String rulePackageVersion,
    @Column("pathway_pkg_version") String pathwayPackageVersion,
    @Column("status") ContextSnapshotStatus status,
    @Column("missing_fields") String missingFieldsJson,
    @Column("mapping_status") String mappingStatusJson,
    @Column("quality_status") QualityStatus qualityStatus,
    @Column("trace_id") String traceId,
    @Column("signature") String signature,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy
) {

    public ContextSnapshot(
            Long id,
            String snapshotId,
            String tenantId,
            String orgUnitId,
            String patientId,
            String encounterId,
            String knowledgePackageVersion,
            String rulePackageVersion,
            String pathwayPackageVersion,
            ContextSnapshotStatus status,
            String missingFieldsJson,
            String mappingStatusJson,
            QualityStatus qualityStatus,
            String traceId,
            String signature,
            Instant createdAt,
            String createdBy) {
        this(
            id, snapshotId, tenantId, orgUnitId,
            null, null,
            ContextSnapshotRequest.firstNonBlank(
                knowledgePackageVersion, rulePackageVersion, pathwayPackageVersion),
            patientId, encounterId,
            knowledgePackageVersion, rulePackageVersion, pathwayPackageVersion,
            status, missingFieldsJson, mappingStatusJson,
            qualityStatus, traceId, signature, createdAt, createdBy
        );
    }
}
