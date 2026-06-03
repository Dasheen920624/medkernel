package com.medkernel.engine.followup;

/**
 * 随访任务列表过滤条件。
 */
public record FollowupTaskFilter(
    String patientId,
    String planId,
    FollowupTaskStatus status
) {}
