package com.medkernel.engine.versioning;

/**
 * 组织继承解析后的资产版本。
 *
 * <p>当机构通过 DISABLE 覆盖在解析期停用资产时，{@code disabled=true} 且 {@code version} 为
 * {@code null}（区别于「从未存在」的未命中）；{@code sourceOrgPath} 指向触发停用的机构。
 */
public record ResolvedAssetVersion(
    AssetVersion version,
    String sourceOrgPath,
    boolean inherited,
    boolean overridden,
    boolean disabled,
    InheritanceExplanation explanation
) {}
