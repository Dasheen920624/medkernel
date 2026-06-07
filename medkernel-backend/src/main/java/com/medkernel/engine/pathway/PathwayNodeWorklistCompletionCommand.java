package com.medkernel.engine.pathway;

import java.time.Instant;

/**
 * 路径节点待办闭环命令。
 */
public record PathwayNodeWorklistCompletionCommand(
    String tenantId,
    String patientPathwayId,
    String nodeCode,
    String clockId,
    String completionReason,
    Instant completedAt,
    String traceId,
    String actor
) {}
