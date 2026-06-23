package com.medkernel.engine.authoring;

import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 自动生成候选落入统一资产草稿后的结果。
 */
public record GeneratedAssetDraftResponse(
    String versionId,
    VersionedAssetType assetType,
    String assetIdentity,
    String versionNo,
    AssetVersionStatus status,
    String contentHash,
    String traceId
) {
}
