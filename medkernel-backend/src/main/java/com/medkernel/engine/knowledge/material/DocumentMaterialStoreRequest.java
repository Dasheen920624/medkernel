package com.medkernel.engine.knowledge.material;

/**
 * 文档原件入库请求。资料库后端由运行配置决定，调用方只声明租户、作用域、字节和真实指纹。
 */
public record DocumentMaterialStoreRequest(
    String tenantId,
    String scopeKey,
    byte[] bytes,
    String fileName,
    String contentType,
    String sha256,
    String sourceChannel,
    String actor
) {
}
