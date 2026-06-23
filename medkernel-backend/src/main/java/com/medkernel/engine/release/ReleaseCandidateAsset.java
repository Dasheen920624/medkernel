package com.medkernel.engine.release;

import java.time.Instant;

import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 发布页面可直接选择的精确资产版本。
 *
 * <p>这里只返回资产自身版本，不包含旧容器、手工发布版本或运行时选择参数。
 */
public record ReleaseCandidateAsset(
    ReleaseSourceLayer sourceLayer,
    VersionedAssetType assetType,
    String assetIdentity,
    String versionId,
    String versionNo,
    AssetVersionStatus status,
    String organizationScope,
    String contentHash,
    String sourceRef,
    Instant updatedAt
) {
}
