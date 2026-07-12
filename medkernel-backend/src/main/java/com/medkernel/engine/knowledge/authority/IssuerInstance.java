package com.medkernel.engine.knowledge.authority;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 平台知识权威的实际发布实例。
 *
 * <p>每台发布服务器必须使用独立且不可变的 {@code issuerInstanceId}；宿主地址不属于发布实例身份。
 */
@Table("mk_knowledge_issuer_instance")
public record IssuerInstance(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("authority_id") String authorityId,
    @Column("issuer_instance_id") String issuerInstanceId,
    @Column("display_name") String displayName,
    @Column("status") IssuerInstanceStatus status,
    @Column("last_handover_sequence") long lastHandoverSequence,
    @Column("activated_at") Instant activatedAt,
    @Column("frozen_at") Instant frozenAt,
    @Column("handed_over_at") Instant handedOverAt,
    @Version @Column("lock_version") Long lockVersion,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
