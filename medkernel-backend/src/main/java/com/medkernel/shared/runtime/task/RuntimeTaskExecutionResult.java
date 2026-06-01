package com.medkernel.shared.runtime.task;

import java.util.List;

/**
 * 运行任务执行结果。
 *
 * @param status         结果状态
 * @param message        状态说明
 * @param errorCode      错误码
 * @param totalCount     总数
 * @param successCount   成功数
 * @param failureCount   失败数
 * @param retryableCount 可重试失败数
 * @param failures       失败明细
 */
public record RuntimeTaskExecutionResult(
    RuntimeTaskStatus status,
    String message,
    String errorCode,
    int totalCount,
    int successCount,
    int failureCount,
    int retryableCount,
    List<RuntimeTaskFailureItem> failures
) {
    public RuntimeTaskExecutionResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public static RuntimeTaskExecutionResult completed(String message) {
        return new RuntimeTaskExecutionResult(RuntimeTaskStatus.COMPLETED, message, null, 1, 1, 0, 0, List.of());
    }

    public static RuntimeTaskExecutionResult failed(String errorCode, String message) {
        return new RuntimeTaskExecutionResult(RuntimeTaskStatus.FAILED, message, errorCode, 1, 0, 1, 0, List.of());
    }

    public static RuntimeTaskExecutionResult timeout(String errorCode, String message) {
        return new RuntimeTaskExecutionResult(RuntimeTaskStatus.ESCALATED, message, errorCode, 1, 0, 1, 1, List.of());
    }

    public static RuntimeTaskExecutionResult notConnected(String errorCode, String message) {
        return new RuntimeTaskExecutionResult(RuntimeTaskStatus.NOT_CONNECTED, message, errorCode, 1, 0, 1, 1,
            List.of());
    }

    public static RuntimeTaskExecutionResult partialSuccess(String message,
                                                            int totalCount,
                                                            int successCount,
                                                            int failureCount,
                                                            List<RuntimeTaskFailureItem> failures) {
        int retryable = failures == null ? 0 : (int) failures.stream().filter(RuntimeTaskFailureItem::retryable).count();
        return new RuntimeTaskExecutionResult(
            RuntimeTaskStatus.PARTIAL_SUCCESS,
            message,
            null,
            totalCount,
            successCount,
            failureCount,
            retryable,
            failures
        );
    }
}
