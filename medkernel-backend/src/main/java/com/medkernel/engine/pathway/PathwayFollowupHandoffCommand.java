package com.medkernel.engine.pathway;

import java.util.List;

/**
 * 患者路径结径后的随访交接命令。
 *
 * <p>路径域只传递患者、就诊、病种和任务类型事实，具体随访计划生成由随访域适配器承担。
 */
public record PathwayFollowupHandoffCommand(
    String patientPathwayId,
    String patientId,
    String encounterId,
    String templateId,
    String diseaseCode,
    String riskLevel,
    List<String> taskTypes
) {
    public PathwayFollowupHandoffCommand {
        taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
    }
}
