package com.medkernel.engine.context;

import java.util.List;

/**
 * 医院运行修订及其经过完整性校验的物化资产清单。
 */
public record ClinicalRuntimeReleaseContent(
    ClinicalRuntimeRelease release,
    List<ClinicalRuntimeReleaseItem> items
) {
    public ClinicalRuntimeReleaseContent {
        if (release == null) {
            throw new IllegalArgumentException("运行修订不能为空");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }
}
