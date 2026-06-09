package com.medkernel.engine.versioning;

/**
 * 撤销覆盖批量操作命令。
 */
public record OverrideBatchRevokeCommand(
    String tenantId,
    String operationId,
    String actor,
    String traceId
) {
}
