package com.medkernel.engine.authoring;

import java.util.List;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 统一资产库编目资料响应。
 */
public record AuthoringAssetProfileResponse(
    VersionedAssetType assetType,
    String assetId,
    String category,
    List<String> tags,
    String traceId
) {}
