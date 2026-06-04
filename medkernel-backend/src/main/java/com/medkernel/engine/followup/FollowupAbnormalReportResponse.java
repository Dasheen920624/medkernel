package com.medkernel.engine.followup;

/**
 * 异常回院上报响应。
 */
public record FollowupAbnormalReportResponse(
    String eventId,
    String returnTaskId,
    String notificationEventId,
    String traceId
) {}
