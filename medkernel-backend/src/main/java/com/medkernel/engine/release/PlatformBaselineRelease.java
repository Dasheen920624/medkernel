package com.medkernel.engine.release;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 平台标准版本发布记录。
 *
 * <p>每个修订只追加且不可修改，明细校验码对应全部精确资产版本。
 */
@Table("platform_baseline_release")
public record PlatformBaselineRelease(
    @Id Long id,
    @Column("baseline_release_id") String baselineReleaseId,
    @Column("revision_no") Long revisionNo,
    @Column("manifest_sha256") String manifestSha256,
    @Column("published_at") Instant publishedAt,
    @Column("published_by") String publishedBy,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {
}
