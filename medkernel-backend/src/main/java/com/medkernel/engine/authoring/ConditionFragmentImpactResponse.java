package com.medkernel.engine.authoring;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;

/**
 * 条件片段变更影响分析响应。
 */
public record ConditionFragmentImpactResponse(
    String fragmentId,
    String fragmentCode,
    int versionNo,
    String packageVersion,
    PageResponse<ConditionFragmentAffectedAsset> affectedAssets,
    String impactDigest,
    String traceId
) {
    public ConditionFragmentImpactResponse {
        affectedAssets = affectedAssets == null ? PageResponse.empty(PageRequest.defaults()) : affectedAssets;
    }
}
