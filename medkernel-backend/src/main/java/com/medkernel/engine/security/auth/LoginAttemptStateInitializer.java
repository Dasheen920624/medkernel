package com.medkernel.engine.security.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录尝试状态初始化器：用独立事务隔离并发首次写入的唯一键竞争。
 */
@Repository
public class LoginAttemptStateInitializer {

    private final JdbcTemplate jdbc;

    public LoginAttemptStateInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 创建零计数状态。并发重复初始化会抛唯一键异常，由调用方识别为同一状态已创建。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initialize(String tenantId,
                           String username,
                           String credentialId,
                           Instant now,
                           String traceId) {
        jdbc.update(
            """
            INSERT INTO sys_login_attempt (
                attempt_id, tenant_id, username, credential_id, failed_count,
                locked_until, last_failed_at, created_at, created_by,
                updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, 0, NULL, NULL, ?, 'auth-login', ?, 'auth-login', ?)
            """,
            "lat-" + UUID.randomUUID(),
            tenantId,
            username,
            credentialId,
            Timestamp.from(now),
            Timestamp.from(now),
            traceId);
    }
}
