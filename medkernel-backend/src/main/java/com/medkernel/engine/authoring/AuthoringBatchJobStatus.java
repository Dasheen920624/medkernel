package com.medkernel.engine.authoring;

/**
 * 创作批量任务状态。
 */
public enum AuthoringBatchJobStatus {
    RUNNING,
    SUCCEEDED,
    PARTIAL_SUCCESS,
    FAILED,
    NOT_CONNECTED
}
