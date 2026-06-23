package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 资产内容版本与临床触发点的多值绑定。
 *
 * <p>绑定属于精确版本；规则正文和路径图不再内嵌唯一触发点。所需字段使用 JSON
 * 数组保存标准字段编码，运行前可据此完成输入完整性校验。
 */
@Table("asset_trigger_binding")
public record AssetTriggerBinding(
    @Id Long id,
    @Column("trigger_binding_id") String triggerBindingId,
    @Column("tenant_id") String tenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("version_id") String versionId,
    @Column("trigger_point") String triggerPoint,
    AssetTriggerPurpose purpose,
    @Column("required_fields_json") String requiredFieldsJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
