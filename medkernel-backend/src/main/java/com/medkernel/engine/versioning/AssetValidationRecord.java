package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 精确绑定资产版本和内容哈希的同步安全复核证据。
 */
@Table("asset_validation_record")
public record AssetValidationRecord(
    @Id Long id,
    @Column("validation_id") String validationId,
    @Column("tenant_id") String tenantId,
    @Column("version_id") String versionId,
    @Column("content_hash") String contentHash,
    Boolean passed,
    String summary,
    @Column("validated_at") Instant validatedAt,
    @Column("validated_by") String validatedBy,
    @Column("trace_id") String traceId
) {
}
