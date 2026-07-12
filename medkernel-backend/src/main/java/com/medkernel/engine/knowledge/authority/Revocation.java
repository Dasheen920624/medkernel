package com.medkernel.engine.knowledge.authority;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 单调追加的签名密钥吊销事实。
 *
 * <p>吊销序号与生效发布序号共同阻止旧快照或已围栏密钥继续产生可接受的新包。
 */
@Table("mk_knowledge_key_revocation")
public record Revocation(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("authority_id") String authorityId,
    @Column("revocation_id") String revocationId,
    @Column("revocation_sequence") long revocationSequence,
    @Column("key_id") String keyId,
    @Column("effective_release_sequence") long effectiveReleaseSequence,
    @Column("reason") String reason,
    @Column("signed_by_key_id") String signedByKeyId,
    @Column("signature") String signature,
    @Column("revoked_at") Instant revokedAt,
    @Version @Column("lock_version") Long lockVersion,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
