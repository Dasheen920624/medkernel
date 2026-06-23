package com.medkernel.engine.terminology;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionStatus;

/**
 * 术语资产草稿生成结果。
 */
public record TerminologyAssetDraftResponse(
    String assetIdentity,
    String versionId,
    String versionNo,
    AssetVersionStatus status,
    String organizationScope,
    String contentHash,
    int mappingCount
) {

    static TerminologyAssetDraftResponse from(AssetVersion version, int mappingCount) {
        return new TerminologyAssetDraftResponse(
            version.assetIdentity(),
            version.versionId(),
            version.versionNo(),
            version.status(),
            version.organizationScope(),
            version.contentHash(),
            mappingCount
        );
    }
}
