package com.medkernel.engine.emrlevel;

/**
 * 电子病历评级闭环证据聚合。
 */
public record EmrLevelClosedLoopEvidenceResponse(
    long cdssCardCount,
    long cdssAcceptedCount,
    long qualityFindingCount,
    long rectificationTaskCount,
    long rectificationClosedCount,
    long auditEventCount
) {
}
