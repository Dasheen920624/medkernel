package com.medkernel.engine.context;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 机构生效版本记录。
 *
 * <p>记录只追加、不修改。每次启用或回退都生成新的机构版本号，锁定平台标准版本与完整版本明细校验码；
 * 当前版本定位就是医院修订号最大的记录。
 */
@Table("clinical_runtime_release")
public record ClinicalRuntimeRelease(
    @Id Long id,
    @Column("release_id") String releaseId,
    @Column("tenant_id") String tenantId,
    @Column("hospital_id") String hospitalId,
    @Column("revision_no") Long revisionNo,
    @Column("platform_baseline_release_id") String platformBaselineReleaseId,
    @Column("manifest_sha256") String manifestSha256,
    @Column("rollback_from_release_id") String rollbackFromReleaseId,
    @Column("activated_at") Instant activatedAt,
    @Column("activated_by") String activatedBy,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {
}
