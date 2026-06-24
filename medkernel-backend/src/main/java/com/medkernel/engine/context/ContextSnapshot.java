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
    @Column("runtime_release_id") String runtimeReleaseId,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("status") ContextSnapshotStatus status,
    @Column("missing_fields") String missingFieldsJson,
    @Column("mapping_status") String mappingStatusJson,
    @Column("extensions_json") String extensionsJson,
    @Column("quality_status") QualityStatus qualityStatus,
    @Column("trace_id") String traceId,
    @Column("signature") String signature,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy
) {}
