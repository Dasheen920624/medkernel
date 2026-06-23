package com.medkernel.engine.authoring;

import java.time.Instant;

import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 独立声明式配置资产列表摘要。
 */
public record DeclarativeAssetSummaryResponse(
    String versionId,
    VersionedAssetType assetType,
    String assetIdentity,
    String versionNo,
    AssetVersionStatus status,
    String organizationScope,
    String applicableScope,
    String sourceRef,
    Instant updatedAt
) {
}
