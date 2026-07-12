package com.medkernel.engine.knowledge.delivery;

import java.util.List;

import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 平台标准版本导出的不可变资源快照输入。
 *
 * <p>该对象只能由平台资源生产主链路提供；导出服务不接受客户端提交正文、许可或测试事实。
 *
 * @param platformReleaseIdentity 平台标准版本稳定标识
 * @param revisionNo 平台标准版本不可变修订号
 * @param platformManifestSha256 平台版本明细 SHA-256
 * @param entries 全部启用和明确停用的稳定资产身份
 * @param activeAssets 全部活动版本的可移植正文输入
 * @param withdrawals 安全撤回及可选替代版本关系
 */
public record FullPackageSnapshot(
    String platformReleaseIdentity,
    long revisionNo,
    String platformManifestSha256,
    List<Entry> entries,
    List<PortableAssetDocument.ExportInput> activeAssets,
    List<Withdrawal> withdrawals
) {
    public FullPackageSnapshot {
        entries = entries == null ? null : List.copyOf(entries);
        activeAssets = activeAssets == null ? null : List.copyOf(activeAssets);
        withdrawals = withdrawals == null ? null : List.copyOf(withdrawals);
    }

    /** 平台版本中的单个稳定资产身份状态。 */
    public record Entry(
        VersionedAssetType assetType,
        String assetIdentity,
        ReleaseEntryState state,
        String versionId,
        String versionNo,
        String sourceContentSha256
    ) {
    }

    /** 已撤回版本及可选的替代版本关系。 */
    public record Withdrawal(
        VersionedAssetType assetType,
        String assetIdentity,
        String withdrawnVersionId,
        String successorVersionId,
        String reasonDigest
    ) {
    }
}
