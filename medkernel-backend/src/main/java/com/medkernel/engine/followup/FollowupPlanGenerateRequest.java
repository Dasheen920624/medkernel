package com.medkernel.engine.followup;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 基于 ACTIVE 标准上下文快照的随访计划生成请求数据契约 (GA-ENG-API-09)。
 */
public record FollowupPlanGenerateRequest(
    @NotBlank String contextSnapshotId,
    String riskLevel,
    List<String> taskTypes,
    String idempotencyKey,
    Boolean modelEnabled,
    String templateId
) {
    public FollowupPlanGenerateRequest {
        taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
    }

    public FollowupPlanGenerateRequest(
            String contextSnapshotId,
            String riskLevel,
            List<String> taskTypes) {
        this(contextSnapshotId, riskLevel, taskTypes, null, null, null);
    }

    public FollowupPlanGenerateRequest(
            String contextSnapshotId,
            String riskLevel,
            List<String> taskTypes,
            String idempotencyKey,
            Boolean modelEnabled) {
        this(contextSnapshotId, riskLevel, taskTypes, idempotencyKey, modelEnabled, null);
    }
}
