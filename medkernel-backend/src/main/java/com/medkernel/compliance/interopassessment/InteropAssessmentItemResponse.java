package com.medkernel.compliance.interopassessment;

import java.util.List;

/**
 * OPT-05 互联互通测评指标项响应。
 */
public record InteropAssessmentItemResponse(
    String itemId,
    String standardVersion,
    InteropAssessmentDimension dimension,
    String itemCode,
    String itemName,
    String requirementSummary,
    InteropAssessmentStatus status,
    int evidenceCount,
    boolean sharedWithEmrLevel,
    String gapReason,
    List<InteropEvidenceResponse> evidences,
    String traceId
) {
    public InteropAssessmentItemResponse {
        evidences = evidences == null ? List.of() : List.copyOf(evidences);
    }
}
