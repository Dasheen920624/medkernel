package com.medkernel.engine.projection;

/**
 * 外部投影执行器结果。
 */
public record ProjectionExecutionResult(
    ProjectionSyncStatus status,
    String message
) {
    public static ProjectionExecutionResult notSynced(String message) {
        return new ProjectionExecutionResult(ProjectionSyncStatus.NOT_SYNCED, message);
    }
}
