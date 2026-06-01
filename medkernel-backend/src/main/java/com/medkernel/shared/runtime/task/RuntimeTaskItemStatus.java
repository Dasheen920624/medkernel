package com.medkernel.shared.runtime.task;

/**
 * 批量任务单项状态，用于部分成功明细。
 */
public enum RuntimeTaskItemStatus {
    /** 单项执行成功。 */
    SUCCESS,
    /** 单项执行失败。 */
    FAILED,
    /** 单项失败但可重试。 */
    RETRYABLE_FAILED
}
