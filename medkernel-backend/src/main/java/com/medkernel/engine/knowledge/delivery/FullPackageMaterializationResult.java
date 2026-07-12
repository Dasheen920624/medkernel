package com.medkernel.engine.knowledge.delivery;

import java.util.List;

import com.medkernel.engine.context.ClinicalRuntimeAssetSelection;

/** 完整包物化后的平台基线与可直接用于机构激活的活动资产集合。 */
public record FullPackageMaterializationResult(
    String platformBaselineReleaseId,
    long releaseSequence,
    List<ClinicalRuntimeAssetSelection> activeAssets
) {
    public FullPackageMaterializationResult {
        activeAssets = List.copyOf(activeAssets);
    }
}
