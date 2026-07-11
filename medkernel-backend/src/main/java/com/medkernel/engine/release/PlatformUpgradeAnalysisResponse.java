package com.medkernel.engine.release;

import java.time.Instant;
import java.util.List;

/**
 * 平台标准版本升级到机构生效版本前的只读差异、冲突与影响分析。
 */
public record PlatformUpgradeAnalysisResponse(
    String analysisDigest,
    Instant generatedAt,
    boolean runtimeMutation,
    PlatformUpgradeBaselineSnapshot targetBaseline,
    PlatformUpgradeRuntimeSnapshot currentRuntime,
    PlatformUpgradeDiffSummary diffSummary,
    List<PlatformUpgradeDiffItem> items
) {
    public PlatformUpgradeAnalysisResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
