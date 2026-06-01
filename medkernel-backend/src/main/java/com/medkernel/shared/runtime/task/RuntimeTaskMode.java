package com.medkernel.shared.runtime.task;

/**
 * SYS-05 运行任务模式。
 */
public enum RuntimeTaskMode {
    /** 同步在线执行，超时需诚实升级且不阻断主流程。 */
    ONLINE,
    /** 异步入队，调用方通过状态查询轮询。 */
    ASYNC,
    /** 批量执行，必须返回成功、失败和可重试明细。 */
    BATCH,
    /** 离线执行，只依赖本地 payload 与本地执行器。 */
    OFFLINE
}
