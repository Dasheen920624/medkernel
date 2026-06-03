package com.medkernel.engine.integration.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 院内异构系统接入适配器实体 (IntegrationAdapter) - Spring Data JDBC Record 风格。
 */
@Table("integration_adapter")
public record IntegrationAdapter(
    @Id Long id,
    @Column("adapter_id") String adapterId,
    @Column("tenant_id") String tenantId,
    @Column("name") String name,
    @Column("protocol_type") String protocolType,
    @Column("status") String status, // ACTIVE, SUSPENDED
    @Column("config_json") String configJson,
    @Column("health_status") String healthStatus, // NOT_CONNECTED(未探活) / MISCONFIGURED(配置非法) / HEALTHY(接入真实连接器后)
    @Column("rtt_ms") Long rttMs,
    @Column("last_heartbeat_at") Instant lastHeartbeatAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
    /**
     * 记录一次健康检查结果：更新 healthStatus、rttMs 与心跳时间。
     *
     * <p>保留 configJson 原值，仅更新健康检查相关字段。
     */
    public IntegrationAdapter withHealthCheck(String newHealthStatus, Long newRtt, Instant timestamp) {
        return new IntegrationAdapter(id, adapterId, tenantId, name, protocolType, status, configJson, newHealthStatus, newRtt, timestamp, createdAt, createdBy, Instant.now(), updatedBy);
    }

    public IntegrationAdapter withUpdate(String newName, String newProtocol, String newConfig, String newStatus) {
        return new IntegrationAdapter(id, adapterId, tenantId, newName, newProtocol, newStatus, newConfig, healthStatus, rttMs, lastHeartbeatAt, createdAt, createdBy, Instant.now(), updatedBy);
    }
}
