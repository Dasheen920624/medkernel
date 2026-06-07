package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Map;

import com.medkernel.engine.context.MissingFieldEntry;
import com.medkernel.engine.context.QualityStatus;

/**
 * 路径回放中的单个快照步骤。
 *
 * <p>记录每个真实上下文快照对应的节点轨迹、最终状态和数据质量证据。
 */
public record PathwaySimulationReplayStep(
    String snapshotId,
    List<String> nodeTrajectory,
    PatientPathwayStatus finalStatus,
    QualityStatus contextQualityStatus,
    List<MissingFieldEntry> missingFields,
    Map<String, String> mappingStatus,
    Map<String, Integer> contextResourceCounts
) {
    public PathwaySimulationReplayStep {
        nodeTrajectory = nodeTrajectory == null ? List.of() : List.copyOf(nodeTrajectory);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        mappingStatus = mappingStatus == null ? Map.of() : Map.copyOf(mappingStatus);
        contextResourceCounts = contextResourceCounts == null ? Map.of() : Map.copyOf(contextResourceCounts);
    }
}
