package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 知识包列表筛选条件。
 */
public record PackageListFilter(
    String keyword,
    KnowledgePackageStatus status,
    VersionedAssetType assetType
) {
}
