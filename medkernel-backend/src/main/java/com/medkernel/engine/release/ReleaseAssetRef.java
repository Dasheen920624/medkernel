package com.medkernel.engine.release;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 发布清单中的稳定资产身份引用。
 */
public record ReleaseAssetRef(
    VersionedAssetType assetType,
    String assetIdentity
) {
}
