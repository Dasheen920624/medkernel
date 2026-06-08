package com.medkernel.engine.authoring;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 配置包批量离线导出请求。
 */
public record AuthoringBatchPackageExportRequest(
    @NotEmpty @Size(max = 50) List<@Valid AuthoringBatchPackageExportItem> items
) {
    public AuthoringBatchPackageExportRequest {
        items = items == null ? null : List.copyOf(items);
    }
}
