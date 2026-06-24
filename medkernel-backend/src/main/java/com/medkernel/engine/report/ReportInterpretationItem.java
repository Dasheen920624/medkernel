package com.medkernel.engine.report;

import java.util.List;

/**
 * 单份医技报告的解读结果。
 */
public record ReportInterpretationItem(
    String reportId,
    String reportType,
    String conclusion,
    String itemCode,
    String itemName,
    Long sourceVersionId,
    String versionNo,
    boolean criticalRisk,
    String summary,
    List<String> abnormalHighlights,
    List<String> recommendations
) {
    public ReportInterpretationItem {
        abnormalHighlights = abnormalHighlights == null ? List.of() : List.copyOf(abnormalHighlights);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }
}
