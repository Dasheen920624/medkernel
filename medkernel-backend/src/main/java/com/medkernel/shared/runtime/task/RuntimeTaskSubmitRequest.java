package com.medkernel.shared.runtime.task;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SYS-05 运行任务提交请求。
 *
 * @param mode        运行模式
 * @param taskType    任务类型，必须由真实执行器显式支持
 * @param payloadJson 任务级 JSON payload
 * @param items       批量模式单项列表
 * @param maxRetries  最大人工重试次数，缺省由服务侧策略决定
 */
public record RuntimeTaskSubmitRequest(
    @NotNull RuntimeTaskMode mode,
    @NotBlank @Size(max = 64) String taskType,
    @Size(max = 1_048_576) String payloadJson,
    @Valid List<RuntimeTaskBatchItem> items,
    @Min(0) @Max(10) Integer maxRetries
) {
    public RuntimeTaskSubmitRequest(RuntimeTaskMode mode,
                                    String taskType,
                                    String payloadJson,
                                    List<RuntimeTaskBatchItem> items) {
        this(mode, taskType, payloadJson, items, null);
    }

    public RuntimeTaskSubmitRequest {
        if (taskType != null) {
            taskType = taskType.trim();
        }
        if (payloadJson == null) {
            payloadJson = "{}";
        }
        items = items == null ? List.of() : List.copyOf(items);
    }
}
