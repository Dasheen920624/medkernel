package com.medkernel.engine.release;

import java.util.List;

import com.medkernel.engine.versioning.ReleaseSimulationResult;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 平台升级分析中的单个资产差异。
 */
public record PlatformUpgradeDiffItem(
    VersionedAssetType assetType,
    String assetIdentity,
    String changeType,
    String currentVersionId,
    String currentVersionNo,
    String currentContentHash,
    String targetVersionId,
    String targetVersionNo,
    String targetContentHash,
    List<ReleaseSimulationResult.Conflict> conflicts
) {
    public PlatformUpgradeDiffItem {
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }
}
