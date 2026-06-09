package com.medkernel.engine.versioning;

import java.util.List;

/**
 * 创建覆盖模板命令。
 */
public record OverrideTemplateCreateCommand(
    String tenantId,
    String templateName,
    String description,
    String applicableScope,
    List<OverrideTemplateItemInput> items,
    String actor,
    String traceId
) {
    public OverrideTemplateCreateCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
