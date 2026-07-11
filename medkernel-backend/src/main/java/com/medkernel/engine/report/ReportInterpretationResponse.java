package com.medkernel.engine.report;

import java.util.List;

/**
 * 医技报告解读响应：只返回辅助解释和复核建议，不改写已签发报告。
 */
public record ReportInterpretationResponse(
    String contextSnapshotId,
    String runtimeReleaseId,
    List<ReportInterpretationItem> interpretations,
    List<String> recommendationCardIds,
    String advisoryNote,
    String traceId
) {
    public ReportInterpretationResponse(
            String contextSnapshotId,
            String runtimeReleaseId,
            List<ReportInterpretationItem> interpretations,
            String advisoryNote,
            String traceId) {
        this(contextSnapshotId, runtimeReleaseId, interpretations, List.of(), advisoryNote, traceId);
    }

    public ReportInterpretationResponse {
        interpretations = interpretations == null ? List.of() : List.copyOf(interpretations);
        recommendationCardIds = recommendationCardIds == null ? List.of() : List.copyOf(recommendationCardIds);
    }
}
