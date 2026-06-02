package com.medkernel.engine.versioning;

/**
 * 历史重放结果。
 */
public record VersionReplayResult(
    VersionReplayBinding binding,
    AssetVersion version,
    String replaySummary
) {}
