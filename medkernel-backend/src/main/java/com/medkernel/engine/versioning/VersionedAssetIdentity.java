package com.medkernel.engine.versioning;

/**
 * 统一版本资产的稳定身份，由资产类型与资产编码共同确定。
 */
public record VersionedAssetIdentity(
    VersionedAssetType assetType,
    String assetIdentity
) {}
