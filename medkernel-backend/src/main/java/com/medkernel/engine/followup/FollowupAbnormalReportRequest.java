package com.medkernel.engine.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 随访临床异常事件上报请求 (GA-ENG-API-09)。
 */
public record FollowupAbnormalReportRequest(
    @NotBlank String planId,
    @NotNull FollowupEventType eventType,
    @NotBlank String payload,
    String triggeredBy,
    String idempotencyKey
) {
    public FollowupAbnormalReportRequest(
            String planId,
            FollowupEventType eventType,
            String payload,
            String triggeredBy) {
        this(planId, eventType, payload, triggeredBy, null);
    }
}
