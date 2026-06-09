package com.medkernel.engine.versioning;

import java.util.List;

/**
 * 覆盖批量预演结果。
 */
public record OverrideBatchPreviewResult(
    String previewDigest,
    String operationType,
    List<Row> rows,
    boolean releasable
) {
    public OverrideBatchPreviewResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public record Row(
        String sourceId,
        String targetOrgUnitId,
        VersionedAssetType assetType,
        String assetIdentity,
        InheritanceOverrideMode overrideMode,
        InheritancePropagation propagation,
        String applicableScope,
        String inheritedVersionId,
        String targetVersionId,
        String diffSummary,
        String overrideReason,
        String status,
        String issue
    ) {
    }
}
