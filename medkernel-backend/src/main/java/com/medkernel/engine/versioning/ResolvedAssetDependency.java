package com.medkernel.engine.versioning;

/**
 * 同一解析上下文中协同解析出的依赖资产。
 */
public record ResolvedAssetDependency(
    AssetDependency edge,
    ResolvedAssetVersion resolved
) {}
