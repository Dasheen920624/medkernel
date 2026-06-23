package com.medkernel.engine.authoring;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.context.MissingFieldEntry;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.rule.ConditionEvidence;
import com.medkernel.engine.rule.RuleActionResult;

/**
 * 草稿即配即试结果。
 *
 * <p>聚合规则命中、路径推进、快照质量与条件证据，供创作页就地定位问题。
 */
public record AuthoringPreviewRunResponse(
    AuthoringPreviewSubject subject,
    String snapshotId,
    String runtimeReleaseId,
    boolean matched,
    Boolean hit,
    String outcomeText,
    String severity,
    List<RuleActionResult> actions,
    JsonNode explanation,
    List<ConditionEvidence> conditionEvidence,
    QualityStatus contextQualityStatus,
    List<MissingFieldEntry> missingFields,
    Map<String, String> mappingStatus,
    Map<String, Integer> contextResourceCounts,
    List<String> nodeTrajectory,
    String finalStatus,
    String selectedEdgeCode,
    String traceId
) {
    public AuthoringPreviewRunResponse {
        actions = actions == null ? List.of() : List.copyOf(actions);
        conditionEvidence = conditionEvidence == null ? List.of() : List.copyOf(conditionEvidence);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        mappingStatus = mappingStatus == null ? Map.of() : Map.copyOf(mappingStatus);
        contextResourceCounts = contextResourceCounts == null ? Map.of() : Map.copyOf(contextResourceCounts);
        nodeTrajectory = nodeTrajectory == null ? List.of() : List.copyOf(nodeTrajectory);
    }
}
