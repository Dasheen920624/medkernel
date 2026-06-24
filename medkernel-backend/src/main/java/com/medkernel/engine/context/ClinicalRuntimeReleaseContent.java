package com.medkernel.engine.context;

import java.util.List;

/**
 * 机构生效版本及其经过完整性校验的物化版本明细。
 */
public record ClinicalRuntimeReleaseContent(
    ClinicalRuntimeRelease release,
    List<ClinicalRuntimeReleaseItem> items
) {
    public ClinicalRuntimeReleaseContent {
        if (release == null) {
            throw new IllegalArgumentException("机构生效版本不能为空");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }
}
