package com.medkernel.engine.versioning;

/**
 * 医院运行修订中解析出的声明式资产正文。
 */
public record ResolvedDeclarativeAsset(
    VersionedAssetType assetType,
    String assetIdentity,
    String assetVersion,
    String runtimeReleaseId,
    String contentJson,
    String contentHash
) {
}
