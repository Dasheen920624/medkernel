package com.medkernel.engine.versioning;

/**
 * 灰度发布计划回退命令。
 */
public record VersionRolloutRollbackCommand(
    String tenantId,
    String planId,
    String reason,
    Boolean confirmedHighRisk,
    String actor,
    String traceId
) {
}
