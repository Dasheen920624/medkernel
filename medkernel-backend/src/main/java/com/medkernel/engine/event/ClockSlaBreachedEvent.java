package com.medkernel.engine.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 路径关键时钟 SLA 触达超时升级事件。
 */
public record ClockSlaBreachedEvent(
    String tenantId,
    String traceId,
    String runtimeReleaseId,
    String patientPathwayId,
    String patientId,
    String encounterId,
    String clockId,
    String nodeCode,
    String metricCode,
    String escalationLevel,
    Instant dueAt,
    Instant occurredAt
) {
    public ClockSlaBreachedEvent {
        tenantId = Objects.requireNonNull(tenantId, "关键时钟事件租户不能为空");
        traceId = Objects.requireNonNull(traceId, "关键时钟事件 trace 不能为空");
        runtimeReleaseId = Objects.requireNonNull(runtimeReleaseId, "关键时钟事件运行修订不能为空");
        patientPathwayId = Objects.requireNonNull(patientPathwayId, "关键时钟事件患者路径 ID 不能为空");
        clockId = Objects.requireNonNull(clockId, "关键时钟事件时钟 ID 不能为空");
        nodeCode = Objects.requireNonNull(nodeCode, "关键时钟事件节点编码不能为空");
        escalationLevel = Objects.requireNonNull(escalationLevel, "关键时钟事件升级级别不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "关键时钟事件发生时间不能为空");
    }
}
