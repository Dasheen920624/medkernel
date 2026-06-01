package com.medkernel.shared.runtime.task;

import java.time.Instant;
import java.util.List;

/**
 * 运行任务状态响应。
 *
 * @param taskId         任务 ID
 * @param mode           运行模式
 * @param status         当前状态
 * @param taskType       任务类型
 * @param totalCount     总数
 * @param successCount   成功数
 * @param failureCount   失败数
 * @param retryableCount 可重试失败数
 * @param retryCount     已人工重试次数
 * @param maxRetries     最大人工重试次数
 * @param nextAttemptAt  下次建议重试时间
 * @param deadLetterId   死信 ID
 * @param replayedFromTaskId 回放来源任务 ID
 * @param failures       失败明细
 * @param message        状态说明
 * @param errorCode      错误码
 * @param traceId        追踪 ID
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 */
public record RuntimeTaskResponse(
    String taskId,
    RuntimeTaskMode mode,
    RuntimeTaskStatus status,
    String taskType,
    int totalCount,
    int successCount,
    int failureCount,
    int retryableCount,
    int retryCount,
    int maxRetries,
    Instant nextAttemptAt,
    String deadLetterId,
    String replayedFromTaskId,
    List<RuntimeTaskFailureItem> failures,
    String message,
    String errorCode,
    String traceId,
    Instant createdAt,
    Instant updatedAt
) {
    public RuntimeTaskResponse {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
