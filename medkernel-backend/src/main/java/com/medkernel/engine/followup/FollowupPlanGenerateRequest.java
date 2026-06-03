package com.medkernel.engine.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 随访计划智能生成请求数据契约 (GA-ENG-API-09)。
 */
public record FollowupPlanGenerateRequest(
    @NotBlank String patientId,
    @NotBlank String encounterId,
    String pathwayId,
    String diseaseCode,
    String riskLevel,
    @NotEmpty List<String> taskTypes,
    String idempotencyKey,
    Boolean modelEnabled
) {
    public FollowupPlanGenerateRequest {
        taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
    }

    public FollowupPlanGenerateRequest(
            String patientId,
            String encounterId,
            String pathwayId,
            String diseaseCode,
            String riskLevel,
            List<String> taskTypes) {
        this(patientId, encounterId, pathwayId, diseaseCode, riskLevel, taskTypes, null, null);
    }
}
