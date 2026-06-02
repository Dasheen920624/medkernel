package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Map;

import com.medkernel.engine.context.MissingFieldEntry;
import com.medkernel.engine.context.QualityStatus;

/**
 * 患者路径推进响应。
 *
 * <p>返回上一个节点、下一节点、推进后状态、命中边、上下文快照和事实证据，便于前端回放执行轨迹。
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
    String traceId
) {

    public PathwayAdvanceResponse {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        mappingStatus = mappingStatus == null ? Map.of() : Map.copyOf(mappingStatus);
        contextResourceCounts = contextResourceCounts == null ? Map.of() : Map.copyOf(contextResourceCounts);
        decisionEvidence = decisionEvidence == null ? Map.of() : Map.copyOf(decisionEvidence);
    }

    public PathwayAdvanceResponse(
            String patientPathwayId,
            String previousNodeCode,
            String nextNodeCode,
            PatientPathwayStatus status,
            String varianceId,
            String traceId) {
        this(patientPathwayId, previousNodeCode, nextNodeCode, status, varianceId,
            null, null, null, null, List.of(), Map.of(), Map.of(), Map.of(), traceId);
    }
}
