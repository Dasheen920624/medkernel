package com.medkernel.engine.context;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 租户自定义上下文字段持久化扩展。
 *
 * <p>平台字段目录由 {@link ContextFieldCatalog} 从 canonical 派生（代码内、只读）；
 * 本表保存平台字段元数据覆盖，以及统一落在 {@code extensions.local.*} 命名空间的租户扩展字段。
 * 沿用业务层级 {@code category}（一级）/{@code groupName}（二级）。
 */
@Table("mk_context_field_catalog")
public record ContextFieldCatalogEntry(
    @Id Long id,
    @Column("field_id") String fieldId,
    @Column("tenant_id") String tenantId,
    String category,
    @Column("group_name") String groupName,
    @Column("resource_type") String resourceType,
    @Column("field_path") String fieldPath,
    @Column("display_name") String displayName,
    @Column("data_type") String dataType,
    String unit,
    @Column("code_system") String codeSystem,
    String description,
    String status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId) {

    /** 转换为对外字段目录条目（与平台派生字段同构）。 */
    public ContextFieldDescriptor toDescriptor() {
        return new ContextFieldDescriptor(
            category, groupName, resourceType, fieldPath, displayName, dataType, unit, codeSystem,
            description, "TENANT", fieldId, false);
    }
}
