package com.medkernel.engine.pkg;

import java.util.List;

/**
 * 组织视角下解析后的有效知识包。
 */
public record EffectiveKnowledgePackageResponse(
    String tenantId,
    String targetOrgUnitId,
    String packageId,
    String packageCode,
    String packageVersion,
    List<EffectivePackageItem> items,
    List<EffectivePackageExclusion> excludedItems,
    List<String> warnings
) {
    public EffectiveKnowledgePackageResponse {
        items = List.copyOf(items == null ? List.of() : items);
        excludedItems = List.copyOf(excludedItems == null ? List.of() : excludedItems);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
