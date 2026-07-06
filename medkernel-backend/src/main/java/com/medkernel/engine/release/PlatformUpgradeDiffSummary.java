package com.medkernel.engine.release;

/**
 * 平台升级分析差异汇总。
 */
public record PlatformUpgradeDiffSummary(
    int added,
    int modified,
    int disabled,
    int unchanged,
    int conflictCount
) {
}
