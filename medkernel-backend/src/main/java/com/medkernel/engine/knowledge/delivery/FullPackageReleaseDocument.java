package com.medkernel.engine.knowledge.delivery;

import java.util.List;

import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 包内平台标准版本、停用和撤回的确定性重建文档。
 */
public record FullPackageReleaseDocument(
    String schemaVersion,
    String platformReleaseIdentity,
    long revisionNo,
    String platformManifestSha256,
    List<Entry> entries,
    List<Withdrawal> withdrawals
) {
    /** 单个稳定资产身份在该完整版本中的状态和精确正文绑定。 */
    public record Entry(
        VersionedAssetType assetType,
        String assetIdentity,
        ReleaseEntryState state,
        String versionId,
        String versionNo,
        String sourceContentSha256,
        String exportedContentDigest,
        String assetPath
    ) {
    }

    /** 安全撤回事实；successorVersionId 为空表示尚无替代版本。 */
    public record Withdrawal(
        VersionedAssetType assetType,
        String assetIdentity,
        String withdrawnVersionId,
        String successorVersionId,
        String reasonDigest
    ) {
    }
}
