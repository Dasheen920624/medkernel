package com.medkernel.engine.authoring;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 统一资产库克隆结果。
 */
public record AuthoringAssetCloneResponse(
    VersionedAssetType sourceAssetType,
    String sourceAssetId,
    VersionedAssetType clonedAssetType,
    String clonedAssetId,
    String clonedAssetCode,
    String status
) {}
