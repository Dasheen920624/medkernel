package com.medkernel.shared.config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.medkernel.shared.api.error.ApiException;

/**
 * 配置中心 JDBC 仓储，关系库是配置真相源。
 */
@Repository
public class SystemConfigRepository {

    private static final RowMapper<SystemConfigItem> ROW_MAPPER = SystemConfigRepository::mapRow;

    private final JdbcTemplate jdbc;

    public SystemConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SystemConfigItem> listActive(String tenantId, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return jdbc.query("""
                SELECT tenant_id, config_key, config_value, value_type, display_name, risk_level,
                       owner, description, source, protected_flag, active_flag, version, updated_at
                  FROM mk_config_item
                 WHERE tenant_id = ? AND active_flag = 'Y'
                 ORDER BY config_key
                """, ROW_MAPPER, tenantId);
        }
        return jdbc.query("""
            SELECT tenant_id, config_key, config_value, value_type, display_name, risk_level,
                   owner, description, source, protected_flag, active_flag, version, updated_at
              FROM mk_config_item
             WHERE tenant_id = ? AND active_flag = 'Y' AND config_key LIKE ?
             ORDER BY config_key
            """, ROW_MAPPER, tenantId, prefix.trim() + "%");
    }

    public Optional<SystemConfigItem> findActive(String tenantId, String key) {
        List<SystemConfigItem> rows = jdbc.query("""
            SELECT tenant_id, config_key, config_value, value_type, display_name, risk_level,
                   owner, description, source, protected_flag, active_flag, version, updated_at
              FROM mk_config_item
             WHERE tenant_id = ? AND config_key = ? AND active_flag = 'Y'
            """, ROW_MAPPER, tenantId, key);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void insertSeedIfAbsent(SystemConfigSeed seed, String actor) {
        try {
            jdbc.update("""
                INSERT INTO mk_config_item (
                    config_id, tenant_id, config_key, config_value, value_type, display_name,
                    risk_level, owner, description, source, protected_flag, active_flag,
                    version, created_at, created_by, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Y', 1, ?, ?, ?, ?)
                """,
                "cfg-" + UUID.randomUUID(),
                seed.tenantId(),
                seed.key(),
                seed.value(),
                seed.valueType(),
                seed.displayName(),
                seed.risk(),
                seed.owner(),
                seed.description(),
                seed.source(),
                flag(seed.protectedConfig()),
                Timestamp.from(seed.seededAt()),
                actor,
                Timestamp.from(seed.seededAt()),
                actor);
        } catch (DuplicateKeyException ignored) {
            // 已有配置项代表关系库已接管该键，启动种子不得覆盖运维侧真实值。
        }
    }

    public SystemConfigItem updateValue(String tenantId, String key, String value, String actor, String reason) {
        SystemConfigItem before = findActive(tenantId, key)
            .orElseThrow(() -> ApiException.notFound("配置项 " + key));
        Instant now = Instant.now();
        long nextVersion = before.version() + 1;
        int updated = jdbc.update("""
            UPDATE mk_config_item
               SET config_value = ?, source = 'API', version = ?, updated_at = ?, updated_by = ?
             WHERE tenant_id = ? AND config_key = ? AND active_flag = 'Y' AND version = ?
            """,
            value,
            nextVersion,
            Timestamp.from(now),
            actor,
            tenantId,
            key,
            before.version());
        if (updated != 1) {
            throw ApiException.conflict("配置项已被其他操作更新，请刷新后重试");
        }
        jdbc.update("""
            INSERT INTO mk_config_history (
                history_id, tenant_id, config_key, before_value, after_value, change_type,
                reason, version, created_at, created_by
            ) VALUES (?, ?, ?, ?, ?, 'UPDATE', ?, ?, ?, ?)
            """,
            "cfg-hist-" + UUID.randomUUID(),
            tenantId,
            key,
            before.value(),
            value,
            reason,
            nextVersion,
            Timestamp.from(now),
            actor);
        return findActive(tenantId, key)
            .orElseThrow(() -> ApiException.notFound("配置项 " + key));
    }

    private static SystemConfigItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new SystemConfigItem(
            rs.getString("tenant_id"),
            rs.getString("config_key"),
            rs.getString("config_value"),
            rs.getString("value_type"),
            rs.getString("display_name"),
            rs.getString("risk_level"),
            rs.getString("owner"),
            rs.getString("description"),
            rs.getString("source"),
            "Y".equalsIgnoreCase(rs.getString("protected_flag")),
            "Y".equalsIgnoreCase(rs.getString("active_flag")),
            rs.getLong("version"),
            updatedAt == null ? null : updatedAt.toInstant());
    }

    private static String flag(boolean value) {
        return value ? "Y" : "N";
    }
}
