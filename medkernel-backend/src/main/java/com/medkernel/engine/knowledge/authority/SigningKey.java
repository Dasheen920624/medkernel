package com.medkernel.engine.knowledge.authority;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 发布实例签名公钥的生命周期元数据。
 *
 * <p>实体只保存 {@code keyId}、公开证书链和授权边界；签名私钥始终留在外置 HSM/KMS 端口。
 */
@Table("mk_knowledge_signing_key")
public record SigningKey(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("authority_id") String authorityId,
    @Column("issuer_instance_id") String issuerInstanceId,
    @Column("key_id") String keyId,
    @Column("root_fingerprint") String rootFingerprint,
    @Column("certificate_chain_pem") String certificateChainPem,
    @Column("status") SigningKeyStatus status,
    @Column("not_before") Instant notBefore,
    @Column("not_after") Instant notAfter,
    @Column("authorized_from_handover_sequence") long authorizedFromHandoverSequence,
    @Column("authorized_through_handover_sequence") Long authorizedThroughHandoverSequence,
    @Version @Column("lock_version") Long lockVersion,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
