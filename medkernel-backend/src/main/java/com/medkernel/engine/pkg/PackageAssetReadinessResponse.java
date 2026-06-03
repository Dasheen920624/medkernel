package com.medkernel.engine.pkg;

import java.time.Instant;
import java.util.List;

/**
 * 配置资产准备就绪快照。
 *
 * <p>供实施向导与配置包中心读取，不落第二状态表，全部由模板、包版本和发布计划事实复算。
 */
public record PackageAssetReadinessResponse(
    String tenantId,
    boolean ready,
    int templateCount,
    long draftPackageCount,
    long releasedPackageCount,
    long activePackageCount,
    boolean grayscaleReady,
    String readyPackageId,
    List<String> blockers,
    Instant checkedAt
) {
    public PackageAssetReadinessResponse {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
