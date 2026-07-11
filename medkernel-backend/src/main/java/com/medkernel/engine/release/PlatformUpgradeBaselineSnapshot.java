package com.medkernel.engine.release;

/**
 * 平台升级分析中的目标平台标准版本摘要。
 */
public record PlatformUpgradeBaselineSnapshot(
    String baselineReleaseId,
    long revisionNo,
    String manifestSha256
) {
}
