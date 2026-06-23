package com.medkernel.engine.authoring;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 独立声明式配置资产详情。
 */
public record DeclarativeAssetDetailResponse(
    String versionId,
    VersionedAssetType assetType,
    String assetIdentity,
    String versionNo,
    AssetVersionStatus status,
    String organizationScope,
    String applicableScope,
    String sourceRef,
    String contentHash,
    JsonNode content,
    Instant updatedAt,
    String traceId
) {
}
