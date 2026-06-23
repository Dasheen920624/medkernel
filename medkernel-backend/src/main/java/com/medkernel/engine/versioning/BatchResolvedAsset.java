package com.medkernel.engine.versioning;

/**
 * 批量解析中的单项结果；资产清单由运行修订显式声明，{@code added} 仅保留为向前兼容的内部标志。
 */
public record BatchResolvedAsset(
    VersionedAssetIdentity identity,
    ResolvedAssetVersion resolution,
    boolean added
) {}
