package com.medkernel.engine.terminology;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 术语映射包发布事件流水（PUBLISH / ROLLBACK）。
 *
 * <p>每次发布或回滚都写一条不可变事件记录，用于审计、回滚证据与发布链路重建；
 * 事件类型见 {@link TermPackageReleaseEventType}，发布模式见 {@link PackageReleaseMode}。
 * 回滚事件中 {@code target_package_id} 指向回滚到的目标包。
 */
@Table("term_mapping_package_release")
public record TermMappingPackageRelease(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("package_id") Long packageId,
    @Column("target_package_id") Long targetPackageId,
    @Column("event_type") TermPackageReleaseEventType eventType,
    @Column("release_mode") PackageReleaseMode releaseMode,
    @Column("reason") String reason,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy
) {
}
