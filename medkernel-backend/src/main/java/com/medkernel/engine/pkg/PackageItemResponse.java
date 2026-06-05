package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 知识包子资产响应 DTO。
 */
public record PackageItemResponse(
    String itemId,
    String packageId,
    VersionedAssetType assetType,
    String assetId,
    String assetVersion
) {
    public static PackageItemResponse from(PackageItem entity) {
        return new PackageItemResponse(
            entity.itemId(),
            entity.packageId(),
            entity.assetType(),
            entity.assetId(),
            entity.assetVersion()
        );
    }
}
