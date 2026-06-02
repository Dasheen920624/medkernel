package com.medkernel.engine.versioning;

/**
 * 组织继承解析后的资产版本。
 */
public record ResolvedAssetVersion(
    AssetVersion version,
    String sourceOrgPath,
    boolean inherited,
    boolean overridden,
    InheritanceExplanation explanation
) {}
