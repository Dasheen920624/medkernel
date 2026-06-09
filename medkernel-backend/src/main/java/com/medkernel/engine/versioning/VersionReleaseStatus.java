package com.medkernel.engine.versioning;

/**
 * 版本发布计划状态。
 */
public enum VersionReleaseStatus {
    IN_REVIEW,
    REJECTED,
    APPROVED,
    PUBLISHED,
    GRAY,
    PAUSED,
    ROLLED_BACK,
    FAILED
}
