package com.medkernel.engine.knowledge.authority;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 平台知识权威从当前发布实例迁移到目标实例的签署交接事实。
 *
 * <p>数据库、资料原件、审计、包注册表和信任链摘要必须共同绑定到同一交接清单。
 */
@Table("mk_knowledge_authority_handover")
public record Handover(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("authority_id") String authorityId,
    @Column("handover_id") String handoverId,
    @Column("handover_sequence") long handoverSequence,
    @Column("source_issuer_instance_id") String sourceIssuerInstanceId,
    @Column("target_issuer_instance_id") String targetIssuerInstanceId,
    @Column("expected_active_issuer_instance_id") String expectedActiveIssuerInstanceId,
    @Column("database_digest") String databaseDigest,
    @Column("material_digest") String materialDigest,
    @Column("audit_digest") String auditDigest,
    @Column("registry_digest") String registryDigest,
    @Column("trust_chain_digest") String trustChainDigest,
    @Column("handover_manifest_digest") String handoverManifestDigest,
    @Column("signed_by_key_id") String signedByKeyId,
    @Column("signature") String signature,
    @Column("status") HandoverStatus status,
    @Column("frozen_at") Instant frozenAt,
    @Column("verified_at") Instant verifiedAt,
    @Column("activated_at") Instant activatedAt,
    @Column("aborted_at") Instant abortedAt,
    @Version @Column("lock_version") Long lockVersion,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
