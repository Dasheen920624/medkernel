package com.medkernel.engine.knowledge.production.triage;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * AIK-STD-10 生成期分流上下文。
 */
public record GenerationTriageContext(
    String tenantId,
    String jobCode,
    Long targetIdentityId,
    VersionedAssetType assetType
) {
}
