package com.medkernel.engine.authoring;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 统一资产库个人收藏响应。
 */
public record AuthoringAssetFavoriteResponse(
    VersionedAssetType assetType,
    String assetId,
    boolean favorite,
    String traceId
) {}
