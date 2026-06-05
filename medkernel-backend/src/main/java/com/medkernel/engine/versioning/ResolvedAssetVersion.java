package com.medkernel.engine.versioning;

/**
 * 组织继承解析后的资产版本。
 *
 * <p>当机构通过 DISABLE 覆盖在解析期停用资产时，{@code disabled=true} 且 {@code version} 为
 * {@code null}（区别于「从未存在」的未命中）；{@code sourceOrgPath} 指向触发停用的机构。
 *
 * <p>{@code sourceTier} 标注生效版本取自平台权威基线（{@link SourceTier#PLATFORM}）还是租户组织闭包内的
 * 版本或覆盖（{@link SourceTier#ORG}），供审核台与运行期审计追溯用的是平台还是某机构定制版本。
 */
public record ResolvedAssetVersion(
    AssetVersion version,
    String sourceOrgPath,
    boolean inherited,
    boolean overridden,
    boolean disabled,
    InheritanceExplanation explanation,
    SourceTier sourceTier
) {}
