package com.medkernel.engine.versioning;

/**
 * 版本登记时声明的资产依赖。
 */
public record AssetDependencyDeclaration(
    VersionedAssetType dependsOnAssetType,
    String dependsOnIdentity,
    String minVersionNo,
    String maxVersionNo,
    AssetDependencyKind kind
) {
    public AssetDependencyDeclaration {
        dependsOnIdentity = blankToNull(dependsOnIdentity);
        minVersionNo = blankToNull(minVersionNo);
        maxVersionNo = blankToNull(maxVersionNo);
        kind = kind == null ? AssetDependencyKind.OTHER : kind;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
