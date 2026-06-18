package com.medkernel.shared.runtime.task;

/**
 * SYS-05 运行任务业务执行器。业务域按任务类型注册 handler，shared 层只负责分发和失败留痕。
 */
public interface RuntimeTaskHandler {

    /**
     * 当前 handler 是否支持该任务类型。
     *
     * @param taskType 任务类型
     * @return 支持时返回 true
     */
    boolean supports(String taskType);

    /**
     * 执行真实业务任务。
     *
     * @param command 运行任务命令
     * @return 真实终态
     */
    RuntimeTaskExecutionResult execute(RuntimeTaskExecutionCommand command);
}
