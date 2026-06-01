package com.medkernel.engine.security.auth;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 受控密码重置一次性 token 记录；数据库只保存 token 摘要。
 */
@Table("sys_password_reset_token")
public record PasswordResetToken(
    @Id Long id,
    @Column("reset_id") String resetId,
    @Column("tenant_id") String tenantId,
    @Column("user_id") String userId,
    @Column("username") String username,
    @Column("token_hash") String tokenHash,
    @Column("expires_at") Instant expiresAt,
    @Column("used_at") Instant usedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public boolean usableAt(Instant now) {
        return usedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
