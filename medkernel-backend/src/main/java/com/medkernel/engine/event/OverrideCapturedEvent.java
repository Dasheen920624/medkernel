package com.medkernel.engine.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 规则人工越权捕获事件。
 */
public record OverrideCapturedEvent(
    String tenantId,
    String traceId,
    String packageVersion,
    String overrideId,
    String executionId,
    String ruleId,
    String ruleCode,
    String versionId,
    String patientId,
    String encounterId,
    String actionCode,
    String overrideReason,
    String overriddenBy,
    Instant occurredAt
) {
    public OverrideCapturedEvent {
        tenantId = Objects.requireNonNull(tenantId, "越权事件租户不能为空");
        traceId = Objects.requireNonNull(traceId, "越权事件 trace 不能为空");
        packageVersion = Objects.requireNonNull(packageVersion, "越权事件包版本不能为空");
        overrideId = Objects.requireNonNull(overrideId, "越权事件 ID 不能为空");
        executionId = Objects.requireNonNull(executionId, "越权事件执行 ID 不能为空");
        ruleId = Objects.requireNonNull(ruleId, "越权事件规则 ID 不能为空");
        ruleCode = Objects.requireNonNull(ruleCode, "越权事件规则编码不能为空");
        versionId = Objects.requireNonNull(versionId, "越权事件版本 ID 不能为空");
        actionCode = Objects.requireNonNull(actionCode, "越权事件动作码不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "越权事件发生时间不能为空");
    }
}
