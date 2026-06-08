package com.medkernel.engine.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 规则真实命中事件。
 */
public record RuleFiredEvent(
    String tenantId,
    String traceId,
    String packageVersion,
    String ruleId,
    String ruleCode,
    String versionId,
    String executionId,
    String triggerPoint,
    String eventId,
    String patientId,
    String encounterId,
    String severity,
    List<String> actions,
    Instant occurredAt
) {
    public RuleFiredEvent {
        tenantId = Objects.requireNonNull(tenantId, "规则命中事件租户不能为空");
        traceId = Objects.requireNonNull(traceId, "规则命中事件 trace 不能为空");
        packageVersion = Objects.requireNonNull(packageVersion, "规则命中事件包版本不能为空");
        ruleId = Objects.requireNonNull(ruleId, "规则命中事件规则 ID 不能为空");
        ruleCode = Objects.requireNonNull(ruleCode, "规则命中事件规则编码不能为空");
        versionId = Objects.requireNonNull(versionId, "规则命中事件版本 ID 不能为空");
        executionId = Objects.requireNonNull(executionId, "规则命中事件执行 ID 不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "规则命中事件发生时间不能为空");
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
