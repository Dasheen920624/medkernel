package com.medkernel.shared.audit.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.audit.AuditActorClassifier;

/**
 * 审计事件持久化仓库（JDBC）。
 *
 * <p>本类直接使用 JdbcTemplate，原因：
 * <ul>
 *   <li>需要 {@code SELECT ... FOR UPDATE} 行级锁，超出 Spring Data JDBC 派生方法能力</li>
 *   <li>查询使用动态过滤 + 游标分页，Repository derived query 表达力不足</li>
 *   <li>5 方言一致性已由 Flyway 迁移保证，无需 Spring Data 抽象</li>
 * </ul>
 */
@Repository
public class AuditEventRepository {

    private static final RowMapper<AuditEventRecord> ROW_MAPPER = AuditEventRepository::mapRow;

    private final JdbcTemplate jdbc;

    public AuditEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 写入审计事件。状态固定为 {@code SIGNED}（哈希链已在调用方计算完成）。
     * 调用方应在同一事务中先锁链头 {@link #lockChainHead(String)}。
     */
    @Transactional
    public void insertEvent(AuditEventRecord record) {
        jdbc.update(
            """
            INSERT INTO audit_event (
                event_id, trace_id, occurred_at, actor_user_id, action,
                resource_type, resource_id, summary, payload_digest,
                tenant_id, hospital_id, department_id,
                actor_roles, org_path, environment_key, before_snapshot, after_snapshot, dedupe_key,
                prev_event_id, prev_signature, signature, status,
                outcome, error_code
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            record.eventId(),
            record.traceId(),
            Timestamp.from(record.occurredAt()),
            record.actorUserId(),
            record.action(),
            record.resourceType(),
            record.resourceId(),
            record.summary(),
            record.payloadDigest(),
            record.tenantId(),
            record.hospitalId(),
            record.departmentId(),
            record.actorRoles(),
            record.orgPath(),
            record.environmentKey(),
            record.beforeSnapshot(),
            record.afterSnapshot(),
            record.dedupeKey(),
            record.prevEventId(),
            record.prevSignature(),
            record.signature(),
            record.status(),
            record.outcome(),
            record.errorCode()
        );
    }

    /**
     * 锁定指定租户的链头并返回当前快照；不存在时返回空。
     * 必须在调用方的事务内执行；事务结束才释放行锁。
     */
    @Transactional
    public Optional<ChainHead> lockChainHead(String tenantId) {
        List<ChainHead> rows = jdbc.query(
            "SELECT tenant_id, last_event_id, last_signature FROM audit_chain_head WHERE tenant_id = ? FOR UPDATE",
            (rs, n) -> new ChainHead(
                rs.getString("tenant_id"),
                rs.getString("last_event_id"),
                rs.getString("last_signature")),
            tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 创建租户初始链头（{@code last_signature = "GENESIS"}）。
     * 重复调用因 PK 冲突抛 {@link DuplicateKeyException}，由调用方吞掉；
     * 使用 {@code REQUIRES_NEW} 让重复插入的回滚不污染外层事务。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initChainHead(String tenantId) {
        jdbc.update(
            "INSERT INTO audit_chain_head (tenant_id, last_event_id, last_signature) VALUES (?, NULL, 'GENESIS')",
            tenantId);
    }

    @Transactional
    public void advanceChainHead(String tenantId, String eventId, String signature) {
        jdbc.update(
            "UPDATE audit_chain_head SET last_event_id = ?, last_signature = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?",
            eventId, signature, tenantId);
    }

    /**
     * 按当前租户游标分页查询审计事件，过滤条件可选。
     * 仅由 {@code AuditQueryService} 调用，调用方负责注入正确的 tenantId。
     */
    public List<AuditEventRecord> findPage(String tenantId, AuditEventQuery query) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, event_id, trace_id, occurred_at, actor_user_id, action,
                   resource_type, resource_id, summary, payload_digest,
                   tenant_id, hospital_id, department_id,
                   actor_roles, org_path, environment_key, before_snapshot, after_snapshot, dedupe_key,
                   prev_event_id, prev_signature, signature, status,
                   outcome, error_code, created_at
              FROM audit_event
             WHERE tenant_id = ?
            """);
        List<Object> params = new ArrayList<>();
        List<Integer> types = new ArrayList<>();
        params.add(tenantId);
        types.add(Types.VARCHAR);

        String sortField = query.safeSortField();
        if (!"id".equalsIgnoreCase(sortField)) {
            throw new IllegalArgumentException("审计事件大列表仅允许按 id 排序");
        }
        String sortDirection = query.safeSortDirection();
        if (query.cursor() != null) {
            sql.append(" AND id ").append("ASC".equals(sortDirection) ? ">" : "<").append(" ? ");
            params.add(query.cursor());
            types.add(Types.BIGINT);
        }
        if (query.action() != null) {
            sql.append(" AND action = ? ");
            params.add(query.action());
            types.add(Types.VARCHAR);
        }
        if (query.resourceType() != null) {
            sql.append(" AND resource_type = ? ");
            params.add(query.resourceType());
            types.add(Types.VARCHAR);
        }
        if (query.actorUserId() != null) {
            sql.append(" AND actor_user_id = ? ");
            params.add(query.actorUserId());
            types.add(Types.VARCHAR);
        }
        if (query.orgPathPrefix() != null) {
            sql.append(" AND (org_path = ? OR org_path LIKE ?) ");
            params.add(query.orgPathPrefix());
            params.add(query.orgPathPrefix() + "/%");
            types.add(Types.VARCHAR);
            types.add(Types.VARCHAR);
        }
        if (query.environmentKey() != null) {
            sql.append(" AND environment_key = ? ");
            params.add(query.environmentKey());
            types.add(Types.VARCHAR);
        }
        if (query.outcome() != null) {
            sql.append(" AND outcome = ? ");
            params.add(query.outcome());
            types.add(Types.VARCHAR);
        }
        if (query.superAdminOnly()) {
            appendRoleTokenFilter(sql, params, types);
        }
        if (query.from() != null) {
            sql.append(" AND occurred_at >= ? ");
            params.add(Timestamp.from(query.from()));
            types.add(Types.TIMESTAMP);
        }
        if (query.to() != null) {
            sql.append(" AND occurred_at < ? ");
            params.add(Timestamp.from(query.to()));
            types.add(Types.TIMESTAMP);
        }
        sql.append(" ORDER BY id ").append(sortDirection).append(" ");
        if (query.cursor() == null && query.safeOffset() > 0) {
            sql.append(" OFFSET ? ROWS ");
            params.add(query.safeOffset());
            types.add(Types.BIGINT);
        }
        sql.append(" FETCH FIRST ").append(query.size() + 1).append(" ROWS ONLY ");

        int[] typeArray = types.stream().mapToInt(Integer::intValue).toArray();
        return jdbc.query(sql.toString(), params.toArray(), typeArray, ROW_MAPPER);
    }

    public Optional<AuditEventRecord> findByEventId(String tenantId, String eventId) {
        List<AuditEventRecord> rows = jdbc.query(
            """
            SELECT id, event_id, trace_id, occurred_at, actor_user_id, action,
                   resource_type, resource_id, summary, payload_digest,
                   tenant_id, hospital_id, department_id,
                   actor_roles, org_path, environment_key, before_snapshot, after_snapshot, dedupe_key,
                   prev_event_id, prev_signature, signature, status,
                   outcome, error_code, created_at
              FROM audit_event
             WHERE tenant_id = ? AND event_id = ?
            """,
            ROW_MAPPER,
            tenantId, eventId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<AuditEventRecord> findByDedupeKey(String tenantId, String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            return Optional.empty();
        }
        List<AuditEventRecord> rows = jdbc.query(
            """
            SELECT id, event_id, trace_id, occurred_at, actor_user_id, action,
                   resource_type, resource_id, summary, payload_digest,
                   tenant_id, hospital_id, department_id,
                   actor_roles, org_path, environment_key, before_snapshot, after_snapshot, dedupe_key,
                   prev_event_id, prev_signature, signature, status,
                   outcome, error_code, created_at
              FROM audit_event
             WHERE tenant_id = ? AND dedupe_key = ?
            """,
            ROW_MAPPER,
            tenantId, dedupeKey);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static AuditEventRecord mapRow(ResultSet rs, int n) throws SQLException {
        return new AuditEventRecord(
            rs.getLong("id"),
            rs.getString("event_id"),
            rs.getString("trace_id"),
            toInstant(rs.getTimestamp("occurred_at")),
            rs.getString("actor_user_id"),
            rs.getString("action"),
            rs.getString("resource_type"),
            rs.getString("resource_id"),
            rs.getString("summary"),
            rs.getString("payload_digest"),
            rs.getString("tenant_id"),
            rs.getString("hospital_id"),
            rs.getString("department_id"),
            rs.getString("prev_event_id"),
            rs.getString("prev_signature"),
            rs.getString("signature"),
            rs.getString("status"),
            rs.getString("outcome"),
            rs.getString("error_code"),
            toInstant(rs.getTimestamp("created_at")),
            rs.getString("actor_roles"),
            rs.getString("org_path"),
            rs.getString("environment_key"),
            rs.getString("before_snapshot"),
            rs.getString("after_snapshot"),
            rs.getString("dedupe_key")
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static void appendRoleTokenFilter(StringBuilder sql, List<Object> params, List<Integer> types) {
        List<String> conditions = new ArrayList<>();
        for (String role : AuditActorClassifier.superAdminRoles()) {
            conditions.add("actor_roles = ?");
            params.add(role);
            types.add(Types.VARCHAR);

            conditions.add("actor_roles LIKE ?");
            params.add(role + ",%");
            types.add(Types.VARCHAR);

            conditions.add("actor_roles LIKE ?");
            params.add("%," + role + ",%");
            types.add(Types.VARCHAR);

            conditions.add("actor_roles LIKE ?");
            params.add("%," + role);
            types.add(Types.VARCHAR);
        }
        sql.append(" AND (").append(String.join(" OR ", conditions)).append(") ");
    }

    /** 链头快照。 */
    public record ChainHead(String tenantId, String lastEventId, String lastSignature) {
    }
}
