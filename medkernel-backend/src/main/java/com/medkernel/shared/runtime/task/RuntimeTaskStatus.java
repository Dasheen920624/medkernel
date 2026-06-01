package com.medkernel.shared.runtime.task;

/**
 * SYS-05 PR1 待办类任务状态机。
 */
public enum RuntimeTaskStatus {
    /** 已入队，尚未被 worker 消费。 */
    UNREAD,
    /** 正在执行。 */
    PROCESSING,
    /** 全部完成。 */
    COMPLETED,
    /** 批量任务部分成功。 */
    PARTIAL_SUCCESS,
    /** 执行失败。 */
    FAILED,
    /** 在线超时或需人工升级，主流程未阻断。 */
    ESCALATED
}
