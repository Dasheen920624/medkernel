package com.medkernel.engine.plugin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 插件安全边界 JDBC 仓储。
 */
@Repository
class PluginSecurityRepository {

    private static final RowMapper<PluginRecord> PLUGIN_ROW_MAPPER = PluginSecurityRepository::mapPlugin;
    private static final RowMapper<PluginGrantRecord> GRANT_ROW_MAPPER = PluginSecurityRepository::mapGrant;

    private final JdbcTemplate jdbc;

    PluginSecurityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<PluginRecord> listByTenant(String tenantId) {
        return jdbc.query("""
            SELECT plugin_id, tenant_id, plugin_code, display_name, status, authority_boundary,
                   capabilities_json, version, created_at, updated_at
              FROM mk_plugin_registry
             WHERE tenant_id = ?
             ORDER BY updated_at DESC, plugin_code ASC
            """, PLUGIN_ROW_MAPPER, tenantId);
    }

    Optional<PluginRecord> findByTenantAndPluginId(String tenantId, String pluginId) {
        List<PluginRecord> rows = jdbc.query("""
            SELECT plugin_id, tenant_id, plugin_code, display_name, status, authority_boundary,
                   capabilities_json, version, created_at, updated_at
              FROM mk_plugin_registry
             WHERE tenant_id = ? AND plugin_id = ?
            """, PLUGIN_ROW_MAPPER, tenantId, pluginId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    PluginRecord insertPlugin(String tenantId,
                              String pluginId,
                              String pluginCode,
                              String displayName,
                              PluginStatus status,
                              PluginAuthorityBoundary boundary,
                              String capabilitiesJson,
                              String actor,
                              String traceId) {
        Instant now = Instant.now();
        jdbc.update("""
            INSERT INTO mk_plugin_registry (
                plugin_id, tenant_id, plugin_code, display_name, status, authority_boundary,
                capabilities_json, version, created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)
            """,
            pluginId,
            tenantId,
            pluginCode,
            displayName,
            status.name(),
            boundary.name(),
            capabilitiesJson,
            Timestamp.from(now),
            actor,
            Timestamp.from(now),
            actor,
            traceId);
        return findByTenantAndPluginId(tenantId, pluginId).orElseThrow();
    }

    PluginRecord updateStatus(String tenantId,
                              String pluginId,
                              PluginStatus status,
                              String actor,
                              String traceId) {
        Instant now = Instant.now();
        int updated = jdbc.update("""
            UPDATE mk_plugin_registry
               SET status = ?, version = version + 1, updated_at = ?, updated_by = ?, trace_id = ?
             WHERE tenant_id = ? AND plugin_id = ?
            """,
            status.name(),
            Timestamp.from(now),
            actor,
            traceId,
            tenantId,
            pluginId);
        if (updated == 0) {
            return null;
        }
        return findByTenantAndPluginId(tenantId, pluginId).orElseThrow();
    }

    PluginGrantRecord insertGrant(String tenantId,
                                  String pluginId,
                                  String grantId,
                                  PluginCapabilityResponse capability,
                                  String authorizationReason,
                                  boolean clinicalSafetyConfirmed,
                                  String actor,
                                  String traceId) {
        Instant now = Instant.now();
        jdbc.update("""
            INSERT INTO mk_plugin_grant (
                grant_id, plugin_id, tenant_id, capability_key, capability_type,
                service_contract_id, status, authorization_reason, clinical_safety_confirmed,
                version, granted_at, granted_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, 'AUTHORIZED', ?, ?, 1, ?, ?, ?, ?, ?)
            """,
            grantId,
            pluginId,
            tenantId,
            capability.capabilityKey(),
            capability.capabilityType().name(),
            capability.serviceContractId(),
            authorizationReason,
            clinicalSafetyConfirmed ? "Y" : "N",
            Timestamp.from(now),
            actor,
            Timestamp.from(now),
            actor,
            traceId);
        return findGrantById(tenantId, grantId).orElseThrow();
    }

    List<PluginGrantRecord> listGrants(String tenantId, String pluginId) {
        return jdbc.query("""
            SELECT grant_id, plugin_id, tenant_id, capability_key, capability_type,
                   service_contract_id, status, clinical_safety_confirmed, granted_at
              FROM mk_plugin_grant
             WHERE tenant_id = ? AND plugin_id = ?
             ORDER BY granted_at ASC, capability_key ASC
            """, GRANT_ROW_MAPPER, tenantId, pluginId);
    }

    private Optional<PluginGrantRecord> findGrantById(String tenantId, String grantId) {
        List<PluginGrantRecord> rows = jdbc.query("""
            SELECT grant_id, plugin_id, tenant_id, capability_key, capability_type,
                   service_contract_id, status, clinical_safety_confirmed, granted_at
              FROM mk_plugin_grant
             WHERE tenant_id = ? AND grant_id = ?
            """, GRANT_ROW_MAPPER, tenantId, grantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static PluginRecord mapPlugin(ResultSet rs, int rowNum) throws SQLException {
        return new PluginRecord(
            rs.getString("plugin_id"),
            rs.getString("tenant_id"),
            rs.getString("plugin_code"),
            rs.getString("display_name"),
            PluginStatus.valueOf(rs.getString("status")),
            PluginAuthorityBoundary.valueOf(rs.getString("authority_boundary")),
            rs.getString("capabilities_json"),
            rs.getLong("version"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")));
    }

    private static PluginGrantRecord mapGrant(ResultSet rs, int rowNum) throws SQLException {
        return new PluginGrantRecord(
            rs.getString("grant_id"),
            rs.getString("plugin_id"),
            rs.getString("tenant_id"),
            rs.getString("capability_key"),
            PluginCapabilityType.valueOf(rs.getString("capability_type")),
            rs.getString("service_contract_id"),
            PluginGrantStatus.valueOf(rs.getString("status")),
            "Y".equalsIgnoreCase(rs.getString("clinical_safety_confirmed")),
            toInstant(rs.getTimestamp("granted_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

record PluginRecord(
    String pluginId,
    String tenantId,
    String pluginCode,
    String displayName,
    PluginStatus status,
    PluginAuthorityBoundary authorityBoundary,
    String capabilitiesJson,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
}

record PluginGrantRecord(
    String grantId,
    String pluginId,
    String tenantId,
    String capabilityKey,
    PluginCapabilityType capabilityType,
    String serviceContractId,
    PluginGrantStatus status,
    boolean clinicalSafetyConfirmed,
    Instant grantedAt
) {
}
