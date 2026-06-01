package com.medkernel.shared.runtime.task;

/**
 * 运行任务执行器端口。
 */
public interface RuntimeTaskExecutorPort {

    /**
     * 执行任务并返回真实状态。
     *
     * @param command 任务元数据与 payload 引用
     * @return 执行结果
     */
    RuntimeTaskExecutionResult execute(RuntimeTaskExecutionCommand command);
}
