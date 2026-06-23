package com.medkernel.engine.context;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 医院本次希望启用的一个稳定资产身份。
 *
 * <p>平台资产不传版本 ID，由目标平台基线解析；本地资产传正式版本 ID。
 * 来源租户和来源层均由服务端根据基线或版本真实组织归属推导，调用方不得手填。
 */
public record ClinicalRuntimeAssetSelection(
    VersionedAssetType assetType,
    String assetIdentity,
    String versionId
) {
    public static ClinicalRuntimeAssetSelection platform(
            VersionedAssetType assetType,
            String assetIdentity) {
        return new ClinicalRuntimeAssetSelection(
            assetType,
            assetIdentity,
            null
        );
    }

    public static ClinicalRuntimeAssetSelection local(
            VersionedAssetType assetType,
            String assetIdentity,
            String versionId) {
        return new ClinicalRuntimeAssetSelection(assetType, assetIdentity, versionId);
    }
}
