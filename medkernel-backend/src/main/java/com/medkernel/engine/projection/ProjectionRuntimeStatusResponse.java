package com.medkernel.engine.projection;

import com.medkernel.engine.clinical.model.ClinicalProjectionStatus;

/**
 * 投影运行状态响应。
 */
public record ProjectionRuntimeStatusResponse(
    ProjectionTargetType targetType,
    String tenantId,
    boolean graphProjectionEnabled,
    boolean difyWorkflowEnabled,
    ClinicalProjectionStatus clinicalProjectionStatus,
    ProjectionSyncStatus difyExecutionStatus,
    long snapshotCount,
    String message
) {}
