package com.medkernel.engine.knowledge.authority;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 平台知识权威的公开信任根及受旧根授权的过渡事实。
 *
 * <p>只持久化公开证书、指纹与过渡签名，不保存任何私钥材料。
 */
@Table("mk_knowledge_trust_root")
public record TrustRoot(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("authority_id") String authorityId,
    @Column("root_fingerprint") String rootFingerprint,
    @Column("root_certificate_pem") String rootCertificatePem,
    @Column("predecessor_fingerprint") String predecessorFingerprint,
    @Column("effective_handover_sequence") long effectiveHandoverSequence,
    @Column("status") TrustRootStatus status,
    @Column("valid_from") Instant validFrom,
    @Column("valid_until") Instant validUntil,
    @Column("transition_authorized_by_key_id") String transitionAuthorizedByKeyId,
    @Column("transition_signature") String transitionSignature,
    @Version @Column("lock_version") Long lockVersion,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
