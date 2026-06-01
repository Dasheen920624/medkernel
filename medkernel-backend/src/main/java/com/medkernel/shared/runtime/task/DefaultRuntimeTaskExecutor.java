package com.medkernel.shared.runtime.task;

import org.springframework.stereotype.Component;

/**
 * 默认运行任务执行器。
 *
 * <p>只提供框架自检任务；业务任务必须在对应域接入真实执行器，避免把未接入能力伪造成成功。
 */
@Component
public class DefaultRuntimeTaskExecutor implements RuntimeTaskExecutorPort {

    static final String SELF_CHECK = "RUNTIME_SELF_CHECK";

    @Override
    public RuntimeTaskExecutionResult execute(RuntimeTaskExecutionCommand command) {
        if (command != null && SELF_CHECK.equals(command.taskType())) {
            return RuntimeTaskExecutionResult.completed("运行任务框架自检完成");
        }
        return RuntimeTaskExecutionResult.failed("UNSUPPORTED_TASK_TYPE", "未接入真实执行器，任务未执行");
    }
}
