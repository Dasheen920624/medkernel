package com.medkernel.engine.versioning;

import java.util.List;
import java.util.Map;

/**
 * 覆盖模板应用或跨组织克隆预演命令。
 */
public record OverrideBatchPreviewCommand(
    String tenantId,
    String templateId,
    String sourceOrgUnitId,
    List<String> targetOrgUnitIds,
    Map<String, String> targetVersionIds,
    String actor,
    String traceId
) {
    public OverrideBatchPreviewCommand {
        targetOrgUnitIds = targetOrgUnitIds == null ? List.of() : List.copyOf(targetOrgUnitIds);
        targetVersionIds = targetVersionIds == null ? Map.of() : Map.copyOf(targetVersionIds);
    }
}
