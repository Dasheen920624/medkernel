package com.medkernel.engine.evaluation;

/**
 * 评估运行接收结果响应。
 *
 * <p>返回运行 ID、终态、写入结果数量、质量问题数量、自动派生整改任务数量、
 * 确定性 B0 模型状态和 traceId。
 */
public record EvaluationRunResponse(
    String runId,
    EvaluationRunStatus status,
    int resultCount,
    int findingCount,
    int taskCount,
    EvaluationModelStatus modelStatus,
    String modelDowngradeReason,
    String traceId
) {

    public EvaluationRunResponse {
        modelStatus = modelStatus == null ? EvaluationModelStatus.MODEL_DISABLED : modelStatus;
    }

    public EvaluationRunResponse(
            String runId,
            EvaluationRunStatus status,
            int resultCount,
            int findingCount,
            int taskCount,
            String traceId) {
        this(runId, status, resultCount, findingCount, taskCount,
            EvaluationModelStatus.MODEL_DISABLED, "MODEL_DISABLED_DETERMINISTIC_RULES", traceId);
    }
}
