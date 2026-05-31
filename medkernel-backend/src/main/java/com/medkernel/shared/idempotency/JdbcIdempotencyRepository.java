package com.medkernel.shared.idempotency;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 基于关系库权威源的幂等记录仓储。
 */
@Repository
public class JdbcIdempotencyRepository implements IdempotencyRepository {

    private final JdbcTemplate jdbc;

    public JdbcIdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> findActive(String tenantId, String idempotencyKey, Instant now) {
        return jdbc.query("""
            SELECT tenant_id, idempotency_key, request_hash, request_method, request_path, status,
                   response_status, response_content_type, response_body, result_hash, trace_id,
                   created_at, expires_at
            FROM sys_idempotency
            WHERE tenant_id = ? AND idempotency_key = ? AND expires_at > ?
            """, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(),
            tenantId, idempotencyKey, Timestamp.from(now));
    }

    @Override
    public boolean reserve(IdempotencyRecord record) {
        try {
            jdbc.update("""
                INSERT INTO sys_idempotency (
                    tenant_id, idempotency_key, request_method, request_path, request_hash,
                    status, trace_id, expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.tenantId(),
                record.idempotencyKey(),
                record.requestMethod(),
                record.requestPath(),
                record.requestHash(),
                record.status(),
                record.traceId(),
                Timestamp.from(record.expiresAt()),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.createdAt()));
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public void complete(IdempotencyRecord record) {
        jdbc.update("""
            UPDATE sys_idempotency
            SET status = ?, response_status = ?, response_content_type = ?, response_body = ?,
                result_hash = ?, trace_id = ?, updated_at = ?
            WHERE tenant_id = ? AND idempotency_key = ?
            """,
            record.status(),
            record.responseStatus(),
            record.responseContentType(),
            record.responseBody(),
            record.resultHash(),
            record.traceId(),
            Timestamp.from(Instant.now()),
            record.tenantId(),
            record.idempotencyKey());
    }

    @Override
    public void delete(String tenantId, String idempotencyKey) {
        jdbc.update("DELETE FROM sys_idempotency WHERE tenant_id = ? AND idempotency_key = ?",
            tenantId, idempotencyKey);
    }

    private IdempotencyRecord map(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        return new IdempotencyRecord(
            rs.getString("tenant_id"),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            rs.getString("request_method"),
            rs.getString("request_path"),
            rs.getString("status"),
            nullableInteger(rs, "response_status"),
            rs.getString("response_content_type"),
            rs.getString("response_body"),
            rs.getString("result_hash"),
            rs.getString("trace_id"),
            createdAt.toInstant(),
            expiresAt.toInstant());
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
    }
}
