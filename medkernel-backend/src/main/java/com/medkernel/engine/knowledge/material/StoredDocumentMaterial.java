package com.medkernel.engine.knowledge.material;

/**
 * 已入库的文档原件账本摘要。
 */
public record StoredDocumentMaterial(
    Long id,
    String tenantId,
    String scopeKey,
    String fileUri,
    String sha256,
    String contentType,
    Long byteSize,
    String storageBackend
) {
}
