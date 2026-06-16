package com.medkernel.engine.knowledge.production.shadow;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 生成期影子评测上下文（AIK-STD-06）。
 *
 * <p>承载租户、生产 job、目标身份和资产类型，确保影子评测结果能回溯到候选生成链路。
 */
public record KnowledgeShadowContext(
    String tenantId,
    String jobCode,
    Long targetIdentityId,
    VersionedAssetType assetType
) {
}
