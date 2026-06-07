package com.medkernel.engine.pathway;

import java.time.Instant;
import java.util.List;

/**
 * 患者路径运行态里程碑达成判定。
 *
 * <p>聚合里程碑定义、绑定节点、预期完成点和实际达成时间，用于临床路径时间线展示。
 */
public record PathwayMilestoneRuntimeStatus(
    String milestoneId,
    String phaseCode,
    String phaseName,
    String milestoneCode,
    String name,
    Integer dayOffset,
    Integer expectedOffsetMinutes,
    List<String> nodeCodes,
    PathwayMilestoneStatus status,
    Instant expectedAt,
    Instant achievedAt
) {

    /**
     * 创建不可变状态，并将空节点集合归一为空列表。
     */
    public PathwayMilestoneRuntimeStatus {
        nodeCodes = nodeCodes == null ? List.of() : List.copyOf(nodeCodes);
    }
}
