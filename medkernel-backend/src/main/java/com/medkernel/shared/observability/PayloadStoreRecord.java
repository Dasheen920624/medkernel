package com.medkernel.shared.observability;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 可观测 payload 存储记录。
 *
 * <p>表内只保存脱敏后的引擎输入输出 payload 摘要和 Base64 内容，不作为业务主表权威。
 */
@Table("mk_obs_payload_store")
public record PayloadStoreRecord(
    @Id Long id,
    @Column("payload_id") String payloadId,
    @Column("tenant_id") String tenantId,
    @Column("org_path") String orgPath,
    @Column("entity_type") String entityType,
    @Column("entity_id") String entityId,
    @Column("trace_id") String traceId,
    @Column("storage_type") String storageType,
    @Column("content_type") String contentType,
    @Column("digest") String digest,
    @Column("size_bytes") long sizeBytes,
    @Column("payload_base64") String payloadBase64,
    @Column("payload_uri") String payloadUri,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("deleted_at") Instant deletedAt,
    @Column("deleted_by") String deletedBy
) {

    public PayloadStoreRecord markDeleted(Instant deletedAt, String deletedBy) {
        return new PayloadStoreRecord(
            id, payloadId, tenantId, orgPath, entityType, entityId, traceId,
            storageType, contentType, digest, sizeBytes, payloadBase64, payloadUri,
            createdAt, createdBy, deletedAt, deletedBy
        );
    }
}
