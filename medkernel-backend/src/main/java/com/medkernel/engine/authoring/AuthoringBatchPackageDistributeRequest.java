package com.medkernel.engine.authoring;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 配置包批量分发请求。
 */
public record AuthoringBatchPackageDistributeRequest(
    @NotEmpty @Size(max = 200) List<@Valid AuthoringBatchPackageDistributeItem> items
) {
    public AuthoringBatchPackageDistributeRequest {
        items = items == null ? null : List.copyOf(items);
    }
}
