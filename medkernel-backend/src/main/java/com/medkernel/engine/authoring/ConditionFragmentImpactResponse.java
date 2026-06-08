package com.medkernel.engine.authoring;

import java.util.List;

/**
 * 条件片段变更影响分析响应。
 */
public record ConditionFragmentImpactResponse(
    String fragmentId,
    String fragmentCode,
    int versionNo,
    String packageVersion,
    List<ConditionFragmentAffectedAsset> affectedAssets,
    String impactDigest,
    String traceId
) {
    public ConditionFragmentImpactResponse {
        affectedAssets = affectedAssets == null ? List.of() : List.copyOf(affectedAssets);
    }
}
