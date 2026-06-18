package com.medkernel.engine.datasvc;

import java.time.Instant;

/**
 * D3/D4 字段加密写入回执。
 *
 * <p>回执只返回密文字节长度、不可逆检索 hash 与密钥引用，不返回明文或密文本体，避免 CLI/MCP/审计二次泄漏。
 */
public record EncryptedFieldReceipt(
    Long storedId,
    String scopeKey,
    String fieldName,
    EngineDataLevel dataLevel,
    String searchHash,
    String keyRef,
    String cipherAlgorithm,
    int cipherTextLength,
    Instant storedAt
) {
}
