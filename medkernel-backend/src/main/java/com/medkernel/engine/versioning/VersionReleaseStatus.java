package com.medkernel.engine.versioning;

/**
 * 版本发布计划状态。
 */
public enum VersionReleaseStatus {
    PENDING_REVIEW,
    REVIEW_REJECTED,
    SILENT_OBSERVATION,
    GRAY,
    FULL,
    ROLLBACKED,
    FAILED
}
