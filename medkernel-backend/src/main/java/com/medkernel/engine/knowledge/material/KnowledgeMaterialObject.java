package com.medkernel.engine.knowledge.material;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 文档原件资料库对象账本。只记录受管 URI、真实指纹和审计字段，不保存原文字节。
 */
@Table("mk_knowledge_material_object")
public record KnowledgeMaterialObject(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("scope_key") String scopeKey,
    @Column("file_uri") String fileUri,
    @Column("sha256") String sha256,
    @Column("content_type") String contentType,
    @Column("byte_size") Long byteSize,
    @Column("storage_backend") String storageBackend,
    @Column("source_channel") String sourceChannel,
    @Column("stored_at") Instant storedAt,
    @Column("stored_by") String storedBy
) {
    StoredDocumentMaterial toStored() {
        return new StoredDocumentMaterial(
            id, tenantId, scopeKey, fileUri, sha256, contentType, byteSize, storageBackend);
    }
}
