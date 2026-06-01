package com.medkernel.engine.projection;

/**
 * 投影一致性差异条目。
 */
public record ProjectionDiffItem(
    String factKey,
    String sourceHash,
    String projectionHash
) {}
