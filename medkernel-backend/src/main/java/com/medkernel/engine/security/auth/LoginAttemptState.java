package com.medkernel.engine.security.auth;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 登录失败计数与锁定 / 限流状态，按租户 + 用户名聚合保存。
 */
@Table("sys_login_attempt")
public record LoginAttemptState(
    @Id Long id,
    @Column("attempt_id") String attemptId,
    @Column("tenant_id") String tenantId,
    @Column("username") String username,
    @Column("credential_id") String credentialId,
    @Column("failed_count") int failedCount,
    @Column("locked_until") Instant lockedUntil,
    @Column("last_failed_at") Instant lastFailedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
