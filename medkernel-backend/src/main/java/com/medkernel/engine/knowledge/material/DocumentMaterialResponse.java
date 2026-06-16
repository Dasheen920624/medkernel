package com.medkernel.engine.knowledge.material;

import java.time.Instant;

/**
 * 文档原件读取响应。原文字节以 Base64 返回，调用方必须按 contentType 处理。
 */
public record DocumentMaterialResponse(
    Long id,
    String fileUri,
    String sha256,
    String contentType,
    Long byteSize,
    String storageBackend,
    String sourceChannel,
    Instant storedAt,
    String storedBy,
    String contentBase64
) {
}
