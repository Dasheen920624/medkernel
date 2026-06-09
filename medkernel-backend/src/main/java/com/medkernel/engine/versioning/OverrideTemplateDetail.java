package com.medkernel.engine.versioning;

import java.util.List;

/**
 * 覆盖模板详情。
 */
public record OverrideTemplateDetail(
    OverrideTemplate template,
    List<OverrideTemplateItem> items
) {
    public OverrideTemplateDetail {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
