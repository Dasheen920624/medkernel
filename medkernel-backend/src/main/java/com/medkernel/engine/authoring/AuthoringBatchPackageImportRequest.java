package com.medkernel.engine.authoring;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 配置包批量离线导入请求。
 */
public record AuthoringBatchPackageImportRequest(
    @NotEmpty @Size(max = 50) List<@Valid AuthoringBatchPackageImportItem> items
) {
    public AuthoringBatchPackageImportRequest {
        items = items == null ? null : List.copyOf(items);
    }
}
