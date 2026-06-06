package com.medkernel.engine.emrlevel;

/**
 * 电子病历评级标准项数据质量明细。
 */
public record EmrLevelDataQualityItemResponse(
    String itemCode,
    String itemName,
    String capabilityCode,
    String capabilityName,
    EmrLevelCapabilityStatus capabilityStatus,
    String evidenceRef,
    boolean evidencePresent,
    boolean timely,
    boolean consistent,
    String gapReason,
    String rectificationTaskId,
    String traceId
) {
}
