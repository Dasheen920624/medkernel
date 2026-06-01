package com.medkernel.engine.projection;

/**
 * 投影同步任务状态。
 */
public enum ProjectionSyncStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    NOT_SYNCED
}
