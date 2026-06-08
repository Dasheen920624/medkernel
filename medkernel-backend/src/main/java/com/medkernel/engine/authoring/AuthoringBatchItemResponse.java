package com.medkernel.engine.authoring;

import java.time.Instant;

/**
 * 创作批量任务逐项结果响应。
 */
public record AuthoringBatchItemResponse(
    String itemId,
    AuthoringBatchItemStatus status,
    String targetType,
    String targetId,
    String resultJson,
    String rollbackRef,
    String errorCode,
    String message,
    Instant createdAt
) {}
