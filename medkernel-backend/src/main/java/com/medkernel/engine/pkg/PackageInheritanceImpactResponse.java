package com.medkernel.engine.pkg;

import java.util.List;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 平台上游版本变更对当前租户继承链的影响分析响应。
 */
public record PackageInheritanceImpactResponse(
    String tenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String applicableScope,
    String upstreamBaseVersion,
    String upstreamTargetVersion,
    int autoInheritedCount,
    int rebaseRequiredCount,
    PackageDiffResponse upstreamDiff,
    List<PackageInheritanceImpactTarget> targets
) {
    public PackageInheritanceImpactResponse {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }
}
