package com.medkernel.engine.pkg;

/**
 * 配置包差异中的单个资产变更行。
 */
public record PackageDiffChange(
    PackageDiffChangeType changeType,
    PackageItemAssetType assetType,
    String assetId,
    String baseVersion,
    String targetVersion
) {}
