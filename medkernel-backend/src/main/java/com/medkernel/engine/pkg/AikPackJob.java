package com.medkernel.engine.pkg;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * AIK 生成资产到知识包的装配作业台账。
 */
@Table("mk_aik_pack_job")
public record AikPackJob(
    @Id Long id,
    @Column("job_id") String jobId,
    @Column("tenant_id") String tenantId,
    @Column("package_id") String packageId,
    @Column("package_code") String packageCode,
    @Column("package_version") String packageVersion,
    @Column("item_count") Integer itemCount,
    @Column("asset_manifest") String assetManifest,
    @Column("manifest_sha256") String manifestSha256,
    @Column("status") AikPackJobStatus status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
