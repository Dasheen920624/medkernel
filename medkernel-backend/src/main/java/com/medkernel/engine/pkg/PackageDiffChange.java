package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 配置包差异中的单个资产变更行。
 */
public record PackageDiffChange(
    PackageDiffChangeType changeType,
    VersionedAssetType assetType,
    String assetId,
    String baseVersion,
    String targetVersion
) {}
