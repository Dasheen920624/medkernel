package com.medkernel.engine.release;

import java.util.List;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;

/**
 * 机构生效版本及其完整物化条目。
 */
public record ClinicalRuntimeReleaseDetailResponse(
    ClinicalRuntimeRelease release,
    List<ClinicalRuntimeReleaseItem> items
) {
    public ClinicalRuntimeReleaseDetailResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
