package com.medkernel.engine.versioning;

/**
 * 批量解析中的单项结果；{@code added} 标识该身份来自组织闭包内的 ADD 独有资产。
 */
public record BatchResolvedAsset(
    VersionedAssetIdentity identity,
    ResolvedAssetVersion resolution,
    boolean added
) {}
