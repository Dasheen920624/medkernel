package com.medkernel.engine.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 患者路径变异登记事件。
 */
public record PathwayVarianceRecordedEvent(
    String tenantId,
    String traceId,
    String runtimeReleaseId,
    String patientPathwayId,
    String patientId,
    String encounterId,
    String varianceId,
    String nodeCode,
    String varianceType,
    String reasonCode,
    String responsibleRole,
    String resolutionDecision,
    Instant occurredAt
) {
    public PathwayVarianceRecordedEvent {
        tenantId = Objects.requireNonNull(tenantId, "路径变异事件租户不能为空");
        traceId = Objects.requireNonNull(traceId, "路径变异事件 trace 不能为空");
        runtimeReleaseId = Objects.requireNonNull(runtimeReleaseId, "路径变异事件机构生效版本不能为空");
        patientPathwayId = Objects.requireNonNull(patientPathwayId, "路径变异事件患者路径 ID 不能为空");
        varianceId = Objects.requireNonNull(varianceId, "路径变异事件 ID 不能为空");
        nodeCode = Objects.requireNonNull(nodeCode, "路径变异事件节点编码不能为空");
        varianceType = Objects.requireNonNull(varianceType, "路径变异事件类型不能为空");
        resolutionDecision = Objects.requireNonNull(resolutionDecision, "路径变异事件处置决策不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "路径变异事件发生时间不能为空");
    }
}
