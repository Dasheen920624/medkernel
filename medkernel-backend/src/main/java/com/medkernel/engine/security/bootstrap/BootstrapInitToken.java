package com.medkernel.engine.security.bootstrap;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 首次部署一次性 init token。数据库只保存 SHA-256 摘要，不保存明文 token。
 */
@Table("mk_security_bootstrap_init_token")
public record BootstrapInitToken(
    @Id Long id,
    @Column("token_id") String tokenId,
    @Column("token_hash") String tokenHash,
    @Column("status") String status,
    @Column("expires_at") Instant expiresAt,
    @Column("used_at") Instant usedAt,
    @Column("used_by") String usedBy,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public boolean active() {
        return BootstrapInitTokenStatus.ACTIVE.name().equalsIgnoreCase(status);
    }

    public BootstrapInitToken withStatus(BootstrapInitTokenStatus nextStatus,
                                         Instant usedAt,
                                         String usedBy,
                                         String traceId) {
        return new BootstrapInitToken(
            id, tokenId, tokenHash, nextStatus.name(), expiresAt,
            usedAt, usedBy, createdAt, createdBy, usedAt, usedBy, traceId);
    }
}
