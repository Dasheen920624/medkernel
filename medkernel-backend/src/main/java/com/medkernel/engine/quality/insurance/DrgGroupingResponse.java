package com.medkernel.engine.quality.insurance;

/**
 * DRG/DIP 入组核对响应。
 */
public record DrgGroupingResponse(
    String groupingId,
    DrgGroupingStatus groupingStatus,
    String expectedGroupCode,
    String actualGroupCode,
    String grouperVersion,
    String explanation,
    String traceId
) {}
