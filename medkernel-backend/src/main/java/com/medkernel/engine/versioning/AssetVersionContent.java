package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 统一配置资产的不可变正文。
 *
 * <p>版本元数据与正文分表保存，但通过 {@code tenant_id + version_id} 一一对应。字段目录、值集、
 * 公式、医嘱套餐和动作卡必须保存可恢复正文，禁止只登记哈希后形成不可运行空壳。
 */
@Table("mk_version_asset_content")
public record AssetVersionContent(
    @Id Long id,
    @Column("version_id") String versionId,
    @Column("tenant_id") String tenantId,
    @Column("content_json") String contentJson,
    @Column("content_hash") String contentHash,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
