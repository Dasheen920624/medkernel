package com.medkernel.shared.runtime.task;

import com.medkernel.shared.observability.PayloadRef;

/**
 * 运行任务执行器命令，只携带元数据和 payload 引用。
 *
 * @param taskId         任务 ID
 * @param tenantId       租户 ID
 * @param orgPath        组织路径
 * @param mode           运行模式
 * @param taskType       任务类型
 * @param payloadRef     payload 存储引用
 * @param batchItemCount 批量项数量
 * @param traceId        追踪 ID
 */
public record RuntimeTaskExecutionCommand(
    String taskId,
    String tenantId,
    String orgPath,
    RuntimeTaskMode mode,
    String taskType,
    PayloadRef payloadRef,
    int batchItemCount,
    String traceId
) {
}
