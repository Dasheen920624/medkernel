package com.medkernel.engine.safety;

import java.time.Instant;

/**
 * 安全撤回影响任务摘要。
 */
public record SafetyAffectedTaskResponse(
    String taskKey,
    String taskType,
    String targetType,
    String targetRef,
    String status,
    Instant dueAt
) {}
