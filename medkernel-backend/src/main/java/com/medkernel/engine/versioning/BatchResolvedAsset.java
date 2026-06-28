package com.medkernel.engine.versioning;

/**
 * 批量解析中的单项结果；版本明细由机构生效版本显式声明。
 */
public record BatchResolvedAsset(
    VersionedAssetIdentity identity,
    ResolvedAssetVersion resolution
) {}
