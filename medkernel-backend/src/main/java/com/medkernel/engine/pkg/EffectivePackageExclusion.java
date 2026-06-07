package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 有效知识包解析时被机构覆盖停用的包条目。
 */
public record EffectivePackageExclusion(
    VersionedAssetType assetType,
    String assetId,
    String declaredVersion,
    String reason,
    String sourceOrgPath
) {}
