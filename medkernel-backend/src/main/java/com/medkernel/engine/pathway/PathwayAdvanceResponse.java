package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Map;

import com.medkernel.engine.context.MissingFieldEntry;
import com.medkernel.engine.context.QualityStatus;

/**
 * 患者路径推进响应。
 *
 * <p>返回上一个节点、下一节点、推进后状态、命中边、上下文快照、事实证据和结径随访交接结果，
 * 便于前端回放执行轨迹并接续 D3 随访。
 */
public record PathwayAdvanceResponse(
    String patientPathwayId,
    String previousNodeCode,
    String nextNodeCode,
    PatientPathwayStatus status,
    String varianceId,
    String edgeCode,
    PathwayEdgeType edgeType,
    String snapshotId,
    QualityStatus contextQualityStatus,
    List<MissingFieldEntry> missingFields,
    Map<String, String> mappingStatus,
    Map<String, Integer> contextResourceCounts,
    Map<String, Object> decisionEvidence,
    String followupPlanId,
    int followupTaskCount,
    String followupHandoffStatus,
    List<PathwayOutcomeBinding> outcomeBindings,
    List<PathwayCoordinationWarning> coordinationWarnings,
    String traceId
) {

    public PathwayAdvanceResponse {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        mappingStatus = mappingStatus == null ? Map.of() : Map.copyOf(mappingStatus);
        contextResourceCounts = contextResourceCounts == null ? Map.of() : Map.copyOf(contextResourceCounts);
        decisionEvidence = decisionEvidence == null ? Map.of() : Map.copyOf(decisionEvidence);
        outcomeBindings = outcomeBindings == null ? List.of() : List.copyOf(outcomeBindings);
        coordinationWarnings = coordinationWarnings == null ? List.of() : List.copyOf(coordinationWarnings);
    }

    public PathwayAdvanceResponse(
            String patientPathwayId,
            String previousNodeCode,
            String nextNodeCode,
            PatientPathwayStatus status,
            String varianceId,
            String traceId) {
        this(patientPathwayId, previousNodeCode, nextNodeCode, status, varianceId,
            null, null, null, null, List.of(), Map.of(), Map.of(), Map.of(),
            null, 0, null, List.of(), List.of(), traceId);
    }
}
