package com.medkernel.engine.release;

import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 离线交付文件中的机构生效版本物化资产条目。
 */
public record ClinicalRuntimeReleaseItemOfflineSnapshot(
    String sourceTenantId,
    ReleaseSourceLayer sourceLayer,
    VersionedAssetType assetType,
    String assetIdentity,
    ReleaseEntryState entryState,
    String versionId,
    String versionNo,
    String contentHash
) {
    public static ClinicalRuntimeReleaseItemOfflineSnapshot from(ClinicalRuntimeReleaseItem item) {
        return new ClinicalRuntimeReleaseItemOfflineSnapshot(
            item.sourceTenantId(),
            item.sourceLayer(),
            item.assetType(),
            item.assetIdentity(),
            item.entryState(),
            item.versionId(),
            item.versionNo(),
            item.contentHash()
        );
    }
}
