package com.medkernel.engine.followup;

/**
 * 随访结果回流响应。
 */
public record FollowupResultBackflowResponse(
    String eventId,
    String contextSnapshotId,
    String traceId
) {}
