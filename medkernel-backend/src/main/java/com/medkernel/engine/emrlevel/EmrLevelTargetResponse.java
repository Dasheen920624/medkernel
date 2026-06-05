package com.medkernel.engine.emrlevel;

import java.math.BigDecimal;
import java.util.List;

/**
 * 电子病历评级目标响应。
 */
public record EmrLevelTargetResponse(
    String targetId,
    String hospitalOrgId,
    int targetLevel,
    String standardVersion,
    EmrLevelTargetStatus status,
    int totalItems,
    int satisfiedItems,
    int gapItems,
    BigDecimal progressRate,
    List<EmrLevelGapResponse> gaps,
    String traceId
) {
}
