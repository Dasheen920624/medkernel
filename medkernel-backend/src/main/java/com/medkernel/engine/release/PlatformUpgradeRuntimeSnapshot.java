package com.medkernel.engine.release;

/**
 * 平台升级分析中的当前机构生效版本摘要。
 */
public record PlatformUpgradeRuntimeSnapshot(
    String releaseId,
    long revisionNo,
    String platformBaselineReleaseId,
    String manifestSha256
) {
}
