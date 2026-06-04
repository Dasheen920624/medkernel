package com.medkernel.engine.followup;

import java.util.List;

/**
 * 随访计划详情响应数据契约 (GA-ENG-API-09)。
 */
public record FollowupPlanDetailResponse(
    String planId,
    String tenantId,
    String patientId,
    String encounterId,
    String diseaseCode,
    FollowupPlanStatus status,
    List<FollowupTaskDetailResponse> tasks,
    FollowupModelStatus modelStatus,
    String sourceFactType,
    String sourceFactId,
    String generationRuleCode,
    String generationExplanation
) {
    public FollowupPlanDetailResponse {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        modelStatus = modelStatus == null ? FollowupModelStatus.MODEL_DISABLED : modelStatus;
    }

    public FollowupPlanDetailResponse(
            String planId,
            String tenantId,
            String patientId,
            String encounterId,
            String diseaseCode,
            FollowupPlanStatus status,
            List<FollowupTaskDetailResponse> tasks) {
        this(planId, tenantId, patientId, encounterId, diseaseCode, status, tasks, FollowupModelStatus.MODEL_DISABLED,
            null, null, null, null);
    }

    public FollowupPlanDetailResponse(
            String planId,
            String tenantId,
            String patientId,
            String encounterId,
            String diseaseCode,
            FollowupPlanStatus status,
            List<FollowupTaskDetailResponse> tasks,
            FollowupModelStatus modelStatus) {
        this(planId, tenantId, patientId, encounterId, diseaseCode, status, tasks, modelStatus,
            null, null, null, null);
    }
}
