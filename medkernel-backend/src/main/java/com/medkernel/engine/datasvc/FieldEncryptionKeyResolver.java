package com.medkernel.engine.datasvc;

/**
 * 数据服务 D3/D4 字段级加密主密钥解析器。
 */
@FunctionalInterface
public interface FieldEncryptionKeyResolver {

    String resolve();
}
