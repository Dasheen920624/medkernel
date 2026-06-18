package com.medkernel.engine.datasvc;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 引擎数据服务字段分级元数据。
 *
 * <p>记录字段路径、数据级别、加密要求和允许通道，供 CLI/MCP/模型调用前做最小必要治理裁决。
 */
@Table("mk_engine_data_field_policy")
public record EngineDataFieldPolicy(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("field_path") String fieldPath,
    @Column("data_level") EngineDataLevel dataLevel,
    @Column("encryption_required_flag") String encryptionRequiredFlag,
    @Column("allowed_channel") String allowedChannel,
    @Column("status") String status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
