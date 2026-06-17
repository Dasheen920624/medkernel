package com.medkernel.shared.runtime.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.observability.PayloadRef;

class DefaultRuntimeTaskExecutorTest {

    @Test
    void dispatchesSupportedTaskTypeToRegisteredHandler() {
        RuntimeTaskHandler handler = new RuntimeTaskHandler() {
            @Override
            public boolean supports(String taskType) {
                return "KNOWLEDGE_ACQUISITION_DISCOVERY".equals(taskType);
            }

            @Override
            public RuntimeTaskExecutionResult execute(RuntimeTaskExecutionCommand command) {
                return RuntimeTaskExecutionResult.completed("公域资料调度任务完成");
            }
        };
        DefaultRuntimeTaskExecutor executor = new DefaultRuntimeTaskExecutor(List.of(handler));

        RuntimeTaskExecutionResult result = executor.execute(command("KNOWLEDGE_ACQUISITION_DISCOVERY"));

        assertThat(result.status()).isEqualTo(RuntimeTaskStatus.COMPLETED);
        assertThat(result.message()).contains("公域资料调度任务完成");
    }

    @Test
    void returnsHonestFailureWhenNoHandlerSupportsTaskType() {
        DefaultRuntimeTaskExecutor executor = new DefaultRuntimeTaskExecutor(List.of());

        RuntimeTaskExecutionResult result = executor.execute(command("UNKNOWN_BUSINESS_TASK"));

        assertThat(result.status()).isEqualTo(RuntimeTaskStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("UNSUPPORTED_TASK_TYPE");
        assertThat(result.message()).contains("未接入真实执行器");
    }

    private RuntimeTaskExecutionCommand command(String taskType) {
        return new RuntimeTaskExecutionCommand(
            "task-1",
            "tenant-1",
            "tenant-1",
            RuntimeTaskMode.BATCH,
            taskType,
            new PayloadRef(PayloadRef.STORAGE_INLINE, "sha256:payload", "db://payload", 12, "application/json"),
            1,
            "trace-1");
    }
}
