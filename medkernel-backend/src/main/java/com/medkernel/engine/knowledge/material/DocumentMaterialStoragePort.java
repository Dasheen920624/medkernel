package com.medkernel.engine.knowledge.material;

/**
 * 文档原件资料库存储端口。实现可以是受管本地磁盘、对象存储或 HTTPS 网关，但不得在未配置时回退到临时目录。
 */
public interface DocumentMaterialStoragePort {

    StoredDocumentMaterial store(DocumentMaterialStoreRequest request);

    byte[] fetch(String tenantId, String fileUri);

    boolean exists(String tenantId, String fileUri);

    void delete(String tenantId, String fileUri);
}
