package com.medkernel.engine.context;

import jakarta.validation.constraints.NotBlank;

/**
 * 租户自定义上下文字段维护请求（P2/P5）。前台维护字段目录时提交，沿用业务层级
 * category（一级）/group（二级）。仅元数据，不含业务数据。
 */
public record ContextFieldCatalogUpsertRequest(
    @NotBlank String category,
    @NotBlank String group,
    @NotBlank String resourceType,
    @NotBlank String fieldPath,
    @NotBlank String displayName,
    @NotBlank String dataType,
    String unit,
    String codeSystem,
    String description) {
}
