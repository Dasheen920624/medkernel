package com.medkernel.shared.runtime.task;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 默认运行任务执行器。
 *
 * <p>只内置框架自检任务；业务任务必须在对应域注册 {@link RuntimeTaskHandler}，避免把未接入能力伪造成成功。
 */
@Component
public class DefaultRuntimeTaskExecutor implements RuntimeTaskExecutorPort {

    static final String SELF_CHECK = "RUNTIME_SELF_CHECK";

    private final List<RuntimeTaskHandler> handlers;

    public DefaultRuntimeTaskExecutor(List<RuntimeTaskHandler> handlers) {
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    @Override
    public RuntimeTaskExecutionResult execute(RuntimeTaskExecutionCommand command) {
        if (command != null && SELF_CHECK.equals(command.taskType())) {
            return RuntimeTaskExecutionResult.completed("运行任务框架自检完成");
        }
        for (RuntimeTaskHandler handler : handlers) {
            if (handler.supports(command == null ? null : command.taskType())) {
                return handler.execute(command);
            }
        }
        return RuntimeTaskExecutionResult.failed("UNSUPPORTED_TASK_TYPE", "未接入真实执行器，任务未执行");
    }
}
