package com.medkernel.engine.pkg;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 同步发布目标实体。
 *
 * <p>保存 Dify、Neo4j、医院业务库等各种环境的同步通道信息。
 */
@Table("sync_target")
public record SyncTarget(
    @Id Long id,
    @Column("target_id") String targetId,
    @Column("tenant_id") String tenantId,
    @Column("target_name") String targetName,
    @Column("target_type") SyncTargetType targetType,
    @Column("connection_config") String connectionConfig,
    SyncTargetStatus status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
