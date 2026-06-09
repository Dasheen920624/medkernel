package com.medkernel.engine.versioning;

import java.util.List;

/**
 * 覆盖批量写操作结果。
 */
public record OverrideBatchOperationResult(
    String operationId,
    OverrideOperationStatus status,
    List<String> overrideIds,
    String previewDigest
) {
    public OverrideBatchOperationResult {
        overrideIds = overrideIds == null ? List.of() : List.copyOf(overrideIds);
    }
}
