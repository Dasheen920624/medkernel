package com.medkernel.engine.pathway;

/**
 * 患者路径结径后的随访交接结果。
 */
public record PathwayFollowupHandoffResult(
    String planId,
    int taskCount,
    String status,
    String traceId
) {
    public static PathwayFollowupHandoffResult skipped(String status, String traceId) {
        return new PathwayFollowupHandoffResult(null, 0, status, traceId);
    }
}
