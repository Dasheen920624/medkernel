package com.medkernel.compliance.interopassessment;

import java.math.BigDecimal;
import java.util.List;

/**
 * OPT-05 互联互通测评映射总览响应。
 */
public record InteropAssessmentResponse(
    String standardVersion,
    int totalItems,
    int satisfiedItems,
    int gapItems,
    int missingEvidenceItems,
    BigDecimal satisfactionRate,
    List<InteropAssessmentItemResponse> items,
    String traceId
) {
    public InteropAssessmentResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
