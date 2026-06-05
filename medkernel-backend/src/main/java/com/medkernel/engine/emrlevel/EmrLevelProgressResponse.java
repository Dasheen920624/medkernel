package com.medkernel.engine.emrlevel;

import java.math.BigDecimal;

/**
 * 电子病历评级目标进度响应。
 */
public record EmrLevelProgressResponse(
    String targetId,
    String hospitalOrgId,
    int targetLevel,
    String standardVersion,
    int totalItems,
    int satisfiedItems,
    int gapItems,
    int openGapItems,
    BigDecimal progressRate,
    String traceId
) {
}
