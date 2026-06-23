package com.medkernel.engine.release;

import java.util.List;

/**
 * 平台基线及其完整物化条目。
 */
public record PlatformBaselineDetailResponse(
    PlatformBaselineRelease release,
    List<PlatformBaselineItem> items
) {
    public PlatformBaselineDetailResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
