package com.medkernel.engine.datasvc.export;

/**
 * 引擎数据服务层异步导出作业状态机。对应 {@code mk_engine_data_export_job.status} CHECK 约束。
 *
 * <pre>
 *   PENDING ──worker 领取──&gt; RUNNING ─┬─完成─&gt; SUCCEEDED
 *                                     ├─失败─&gt; FAILED
 *                                     └─取消─&gt; CANCELLED
 *   SUCCEEDED ──TTL 到期──&gt; EXPIRED
 * </pre>
 */
public enum ExportJobStatus {
    /** 已提交，等待 worker 领取 */
    PENDING,
    /** 正在执行 */
    RUNNING,
    /** 成功，result_uri 可下载 */
    SUCCEEDED,
    /** 失败，error_message 含诚实原因 */
    FAILED,
    /** 用户取消 */
    CANCELLED,
    /** 结果文件 TTL 到期，已清理（仍可重发起新作业） */
    EXPIRED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
