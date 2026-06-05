package com.medkernel.engine.emrlevel;

/**
 * 电子病历评级证据来源统计。
 */
public record EmrLevelEvidenceSourceResponse(
    String sourceType,
    long totalCount,
    boolean available,
    String latestTraceId
) {
}
