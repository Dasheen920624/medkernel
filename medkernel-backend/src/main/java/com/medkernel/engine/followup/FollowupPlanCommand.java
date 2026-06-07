package com.medkernel.engine.followup;

import java.util.List;

/**
 * 随访域内部计划生成命令，由标准上下文或路径结径事实装配。
 */
record FollowupPlanCommand(
    String patientId,
    String encounterId,
    String pathwayId,
    String diseaseCode,
    String riskLevel,
    List<String> taskTypes,
    String idempotencyKey,
    Boolean modelEnabled
) {
    FollowupPlanCommand {
        taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
    }
}
