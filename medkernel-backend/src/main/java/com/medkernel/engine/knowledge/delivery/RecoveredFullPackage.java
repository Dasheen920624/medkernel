package com.medkernel.engine.knowledge.delivery;

import com.medkernel.engine.knowledge.authority.PackageSignatureEnvelope;

/**
 * 登记事务回滚后，从受管目录严格重读出的既有完整包事实。
 *
 * @param envelope 包内规范公开签名信封
 * @param stored 已重新计算的整文件摘要、大小和受管坐标
 */
public record RecoveredFullPackage(
    PackageSignatureEnvelope envelope,
    StoredFullPackage stored
) {
}
