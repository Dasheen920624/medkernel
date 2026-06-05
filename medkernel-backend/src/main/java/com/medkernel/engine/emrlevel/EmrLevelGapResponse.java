package com.medkernel.engine.emrlevel;

/**
 * 电子病历评级差距响应。
 */
public record EmrLevelGapResponse(
    String gapId,
    String itemCode,
    String itemName,
    String capabilityCode,
    EmrLevelCapabilityStatus capabilityStatus,
    String gapReason,
    String rectificationTaskId,
    String traceId
) {
}
