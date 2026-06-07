package com.medkernel.engine.pathway;

import java.util.List;

/**
 * 患者路径详情响应。
 *
 * <p>聚合路径运行实例、里程碑状态、变异记录、关键时钟和 traceId，用于临床工作台查看路径执行事实。
 */
public record PatientPathwayDetailResponse(
    PatientPathway patientPathway,
    List<PathwayMilestoneRuntimeStatus> milestoneStatuses,
    List<PathwayVariance> variances,
    List<ClinicalClock> clocks,
    List<PathwayOutcomeBinding> outcomeBindings,
    List<PathwayCoordinationWarning> coordinationWarnings,
    String traceId
) {

    /**
     * 创建不可变患者路径详情，并将空里程碑、变异和时钟集合归一为空列表。
     */
    public PatientPathwayDetailResponse {
        milestoneStatuses = milestoneStatuses == null ? List.of() : List.copyOf(milestoneStatuses);
        variances = variances == null ? List.of() : List.copyOf(variances);
        clocks = clocks == null ? List.of() : List.copyOf(clocks);
        outcomeBindings = outcomeBindings == null ? List.of() : List.copyOf(outcomeBindings);
        coordinationWarnings = coordinationWarnings == null ? List.of() : List.copyOf(coordinationWarnings);
    }

    public PatientPathwayDetailResponse(
            PatientPathway patientPathway,
            List<PathwayMilestoneRuntimeStatus> milestoneStatuses,
            List<PathwayVariance> variances,
            List<ClinicalClock> clocks,
            String traceId) {
        this(patientPathway, milestoneStatuses, variances, clocks, List.of(), List.of(), traceId);
    }
}
