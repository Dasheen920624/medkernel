package com.medkernel.engine.versioning;

/**
 * resolution epoch 中固定的资产版本指纹。
 */
public record ResolutionEpochBinding(
    VersionedAssetType assetType,
    String assetIdentity,
    String versionId,
    String contentHash
) {}
