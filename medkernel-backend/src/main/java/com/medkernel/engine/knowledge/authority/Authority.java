package com.medkernel.engine.knowledge.authority;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 平台知识权威的稳定身份与当前发布游标。
 *
 * <p>{@code authorityId} 与宿主、IP、目录和数据库主键解耦；当前发布实例及序号只能通过后续原子接管服务推进。
 */
@Table("mk_knowledge_authority")
public record Authority(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("authority_id") String authorityId,
    @Column("active_issuer_instance_id") String activeIssuerInstanceId,
    @Column("active_trust_root_fingerprint") String activeTrustRootFingerprint,
    @Column("handover_sequence") long handoverSequence,
    @Column("release_sequence") long releaseSequence,
    @Version @Column("lock_version") Long lockVersion,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
