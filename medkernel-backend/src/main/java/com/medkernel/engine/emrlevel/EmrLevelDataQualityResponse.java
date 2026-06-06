package com.medkernel.engine.emrlevel;

import java.math.BigDecimal;
import java.util.List;

/**
 * 电子病历评级数据质量响应。
 */
public record EmrLevelDataQualityResponse(
    String targetId,
    String hospitalOrgId,
    int targetLevel,
    String standardVersion,
    int totalItems,
    int coveredItems,
    int missingEvidenceItems,
    int gapItems,
    BigDecimal applicationCoverageRate,
    BigDecimal completenessRate,
    BigDecimal timelinessRate,
    BigDecimal consistencyRate,
    EmrLevelClosedLoopEvidenceResponse closedLoopEvidence,
    List<EmrLevelEvidenceSourceResponse> evidenceSources,
    List<EmrLevelDataQualityItemResponse> items,
    String traceId
) {
}
