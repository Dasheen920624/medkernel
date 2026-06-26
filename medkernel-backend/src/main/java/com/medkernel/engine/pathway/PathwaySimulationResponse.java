package com.medkernel.engine.pathway;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.medkernel.engine.context.MissingFieldEntry;
import com.medkernel.engine.context.QualityStatus;

/**
 * 路径试运行响应。
 *
 * <p>返回模板 ID、可选真实快照证据、节点轨迹、最终状态和 traceId，用于在发布或复核前回放路径走向。
 */
public record PathwaySimulationResponse(
    String templateId,
    String snapshotId,
    List<String> nodeTrajectory,
    PatientPathwayStatus finalStatus,
    QualityStatus contextQualityStatus,
    List<MissingFieldEntry> missingFields,
    Map<String, String> mappingStatus,
    Map<String, Integer> contextResourceCounts,
    PathwaySimulationMode simulationMode,
    List<PathwaySimulationReplayStep> replaySteps,
    Instant timeMachineAt,
    String traceId
) {

    /**
     * 创建不可变试运行响应，并将集合字段归一为空集合。
     */
    public PathwaySimulationResponse {
        nodeTrajectory = nodeTrajectory == null ? List.of() : List.copyOf(nodeTrajectory);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        mappingStatus = mappingStatus == null ? Map.of() : Map.copyOf(mappingStatus);
        contextResourceCounts = contextResourceCounts == null ? Map.of() : Map.copyOf(contextResourceCounts);
        simulationMode = simulationMode == null ? PathwaySimulationMode.SINGLE_SNAPSHOT : simulationMode;
        replaySteps = replaySteps == null ? List.of() : List.copyOf(replaySteps);
    }

    public PathwaySimulationResponse(
            String templateId,
            String snapshotId,
            List<String> nodeTrajectory,
            PatientPathwayStatus finalStatus,
            QualityStatus contextQualityStatus,
            List<MissingFieldEntry> missingFields,
            Map<String, String> mappingStatus,
            Map<String, Integer> contextResourceCounts,
            String traceId) {
        this(templateId, snapshotId, nodeTrajectory, finalStatus, contextQualityStatus,
            missingFields, mappingStatus, contextResourceCounts,
            PathwaySimulationMode.SINGLE_SNAPSHOT, List.of(), null, traceId);
    }

    public PathwaySimulationResponse(
            String templateId,
            List<String> nodeTrajectory,
            PatientPathwayStatus finalStatus,
            String traceId) {
        this(templateId, null, nodeTrajectory, finalStatus, null, List.of(), Map.of(), Map.of(), traceId);
    }
}
