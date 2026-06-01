package com.medkernel.shared.runtime.task;

/**
 * 批量任务失败明细。
 *
 * @param itemId    单项 ID
 * @param errorCode 错误码
 * @param message   可展示错误说明
 * @param retryable 是否可重试
 */
public record RuntimeTaskFailureItem(
    String itemId,
    String errorCode,
    String message,
    boolean retryable
) {
}
